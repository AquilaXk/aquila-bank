# AWS EC2 App Baseline Terraform Design

## Goal
- AWS에는 App EC2 1대만 만들고 RDS는 만들지 않는다.
- 기본 스펙은 EC2 `t3.small`, root EBS gp3 40GiB다.
- 1억 건 PostgreSQL 데이터는 로컬 Mac Docker PostgreSQL + 로컬 디스크/volume fixture를 그대로 primary evidence로 사용한다.

## Selected Approach
- 새 VPC와 public subnet 1개를 만들고 EC2 1대를 public subnet에 배치한다.
- EC2는 SSH 접근만 운영자 CIDR로 제한한다.
- 비용 방어를 위해 RDS, private DB subnet, NAT Gateway, ALB/NLB, Elastic IP는 기본 구성에서 제외한다.
- 실제 application 배포, Docker bootstrap, SSL 인증서, domain 연결은 이번 PR 범위에서 제외한다.
- EC2에서 로컬 Mac DB로 붙는 SSH tunnel/VPN/allowlist 설계는 별도 보안 범위로 분리한다.

## Alternatives Considered
- EC2 only: 현재 요구에 맞고 RDS 비용을 만들지 않는다.
- EC2 + RDS baseline: 배포 DB 연결 smoke는 가능하지만 free-plan 제한과 storage 비용 때문에 이번 목표에서 제외한다.
- Full production-like stack: ALB, NAT, ACM, Route53, monitoring까지 포함할 수 있지만 비용과 범위가 커져 이번 목표에는 맞지 않는다.
- Local Docker PostgreSQL primary: 비용과 secret 없이 1억 row fixture를 반복할 수 있어 현재 primary evidence로 사용한다.

## Terraform Structure
- 경로: `infra/terraform/aws/ec2-app-baseline`
- Provider: `hashicorp/aws`
- 주요 파일:
  - `versions.tf`: Terraform/provider 버전 제약
  - `providers.tf`: AWS provider region 연결
  - `variables.tf`: region, CIDR, key pair, AMI, EC2 sizing 변수
  - `locals.tf`: naming, tags
  - `network.tf`: VPC, public subnet, IGW, route table
  - `security.tf`: EC2 security group
  - `compute.tf`: EC2 instance
  - `outputs.tf`: 접속/리소스 output
  - `terraform.tfvars.example`: 실값 없는 예시
  - `README.md`: 실행, 비용, destroy 절차

## Inputs
- `aws_region`
- `name_prefix`
- `ssh_ingress_cidr`
- `ec2_key_name`
- `ec2_ami_id`
- `ec2_instance_type`
- `ec2_root_volume_size`
- `vpc_cidr`, `public_subnet_cidr`

## Cost Boundary
- EC2 `t3.small`과 gp3 40GiB EBS는 free tier가 아닐 수 있다.
- RDS를 만들지 않으므로 DB instance, RDS storage, snapshot 비용은 발생하지 않는다.
- 1억 건 검증 비용은 로컬 Mac Docker PostgreSQL + 로컬 디스크 사용량으로 제한한다.

## Error And Risk Handling
- EC2 `t3.small`도 CPU credit 고갈 시 성능이 급락할 수 있다.
- 로컬 Mac DB를 EC2에 노출하지 않는다. 필요 시 별도 SSH tunnel/VPN 설계와 접근 제어 검토가 먼저 필요하다.
- SSH private key는 Terraform variable/local file로 다루지 않고 기존 AWS key pair name만 참조한다.

## Validation
- `terraform fmt -check`
- `terraform init -backend=false`
- `terraform validate`
- 실제 `plan/apply`는 AWS credentials와 key pair가 필요하다.
