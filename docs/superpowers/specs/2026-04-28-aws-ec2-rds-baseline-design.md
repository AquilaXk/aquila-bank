# AWS EC2/RDS Optional Baseline Terraform Design

## Goal
- 로컬 Docker PostgreSQL + 로컬 디스크 100m primary evidence를 보완하는 선택적 AWS 배포 smoke baseline을 유지한다.
- 기본 스펙은 EC2 `t3.micro`, RDS PostgreSQL `db.t4g.micro`, gp2 20GiB storage이며, free-tier/cost 조건이 맞을 때만 사용한다.
- 1억 건 적재와 목표 달성 판정은 AWS가 아니라 로컬 Docker fixture/k6 archive를 기준으로 닫는다.

## Selected Approach
- 새 VPC를 만들고 public subnet에는 EC2 1대를, private DB subnet 2개에는 RDS subnet group을 둔다.
- EC2는 SSH 접근만 외부 CIDR로 제한하고, RDS는 EC2 security group에서 오는 5432만 허용한다.
- 비용 방어를 위해 NAT Gateway, ALB/NLB, Multi-AZ RDS, Performance Insights, Enhanced Monitoring, Elastic IP는 기본 구성에서 제외한다.
- 실제 application 배포, Docker bootstrap, SSL 인증서, domain 연결은 이번 PR 범위에서 제외한다.

## Alternatives Considered
- EC2 only: 가장 저렴하지만 RDS I/O, connection, private subnet 접근 경로를 검증할 수 없다.
- EC2 + RDS baseline: free-plan 제한 안에서 repo 운영 목표인 EC2/RDS 분리 구조를 선택적으로 검증한다.
- Full production-like stack: ALB, NAT, ACM, Route53, monitoring까지 포함할 수 있지만 비용과 범위가 커져 이번 목표에는 맞지 않는다.
- Local Docker PostgreSQL primary: 비용과 secret 없이 1억 row fixture를 반복할 수 있어 현재 primary evidence로 사용한다.

## Terraform Structure
- 경로: `infra/terraform/aws/ec2-rds-baseline`
- Provider: `hashicorp/aws`
- 주요 파일:
  - `versions.tf`: Terraform/provider 버전 제약
  - `providers.tf`: AWS provider region 연결
  - `variables.tf`: region, CIDR, key pair, AMI, DB credentials, RDS sizing 변수
  - `locals.tf`: naming, tags
  - `network.tf`: VPC, subnets, IGW, route table, DB subnet group
  - `security.tf`: EC2/RDS security groups
  - `compute.tf`: EC2 instance
  - `database.tf`: RDS PostgreSQL instance
  - `outputs.tf`: 접속/리소스 output
  - `terraform.tfvars.example`: 실값 없는 예시
  - `README.md`: 실행, 비용, destroy 절차

## Inputs
- `aws_region`
- `name_prefix`
- `ssh_ingress_cidr`
- `ec2_key_name`
- `ec2_ami_id`
- `vpc_cidr`, `public_subnet_cidr`, `private_db_subnet_cidrs`
- `db_name`, `db_username`, `db_password`
- `db_engine_version`, `db_allocated_storage`, `db_max_allocated_storage`

## Cost Boundary
- EC2 `t3.micro`는 free tier/credit 대상 여부가 계정 생성 시점과 AWS Free Tier 조건에 따라 다르다.
- RDS는 free-plan 허용 타입인 `db.t4g.micro`와 20GiB gp2 storage를 기본값으로 둔다.
- 1억 건 데이터 적재 시 20GiB를 넘을 수 있으므로 paid plan 전환과 storage/class 상향이 필요할 수 있다.
- 비용 조건이 맞지 않으면 이 module을 apply하지 않고 로컬 Docker PostgreSQL + 로컬 디스크 기준으로 1억 건 검증을 진행한다.
- 기본값은 비용 방어를 위해 `multi_az = false`, `backup_retention_period = 1`, `skip_final_snapshot = true`, `deletion_protection = false`로 둔다.

## Error And Risk Handling
- RDS PostgreSQL engine version은 리전별 지원 여부가 달라질 수 있으므로 실제 apply 전 AWS CLI 또는 console로 확인한다.
- EC2 `t3.micro`는 CPU credit 고갈 시 성능이 급락할 수 있다.
- RDS `db.t4g.micro`는 1억 건 조회의 모든 병목을 해결하는 크기가 아니라 free-plan에서 EC2/RDS 분리 구조를 확인하는 optional remote smoke 기준이다.
- RDS password와 SSH private key는 Terraform variable/local file로만 다루고 저장소에 기록하지 않는다.

## Validation
- `terraform fmt -check`
- `terraform init -backend=false`
- `terraform validate`
- 실제 `plan/apply`는 AWS credentials, key pair, AMI ID, DB password가 필요하므로 로컬 자동 검증 범위에서 제외한다.
