# AWS EC2/RDS Baseline Terraform

AWS Free plan 계정에서 실행 가능한 최소 EC2/RDS baseline을 만든다.

## 구성

- EC2: `t3.micro`
- EC2 root EBS: gp3, 기본 30GiB
- RDS: PostgreSQL, `db.t4g.micro`
- RDS storage: gp2, 기본 20GiB
- Network: 새 VPC, public subnet 1개, private DB subnet 2개, Internet Gateway
- Security:
  - SSH `22/tcp`: `ssh_ingress_cidr`에서만 허용
  - PostgreSQL `5432/tcp`: EC2 security group에서만 허용
- 제외: NAT Gateway, ALB/NLB, Multi-AZ RDS, Elastic IP, Enhanced Monitoring, Performance Insights

## 비용 주의

- EC2 `t3.micro` free tier/credit 적용 여부는 계정 상태와 AWS Free Tier 조건에 따라 다르다.
- RDS `db.t4g.micro`와 20GiB gp2 storage는 AWS RDS free tier/free plan 허용 범위에 맞춘 값이다.
- 1억 건 전체 데이터 적재에는 20GiB가 부족할 수 있으며, 이 경우 paid plan 전환과 storage/class 상향이 필요하다.
- 기본값은 테스트 비용 방어를 위해 `multi_az = false`, `backup_retention_period = 1`, `skip_final_snapshot = true`, `deletion_protection = false`다.
- NAT Gateway와 Load Balancer는 고정 비용이 생기므로 기본 구성에서 제외한다.

## 준비 값

- AWS credentials: environment, shared config, SSO 중 하나
- 기존 EC2 key pair name
- 운영자 공인 IP `/32`
- RDS master password
- 필요 시 고정할 RDS PostgreSQL engine version

기본값은 AWS가 리전에서 지원하는 PostgreSQL 기본 버전을 선택하도록 `db_engine_version = null`로 둔다. 특정 버전이 필요하면 적용 전 리전 지원 버전을 확인하고 고정한다.

```bash
aws rds describe-db-engine-versions \
  --engine postgres \
  --engine-version <major-or-minor-version> \
  --region ap-northeast-2
```

## 실행

```bash
cd infra/terraform/aws/ec2-rds-baseline
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에 실제 값을 입력한다. 이 파일은 git에 커밋하지 않는다.
RDS password는 Terraform state에 저장될 수 있으므로 state 파일도 공개 저장소나 공유 채널에 올리지 않는다.

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

## 접속

EC2 접속:

```bash
ssh -i ~/.ssh/<private-key> ec2-user@<ec2_public_ip>
```

RDS는 public 접근을 막고 EC2 security group에서만 접근한다. EC2에서 `psql` 또는 애플리케이션으로 `rds_endpoint:5432`에 접속한다.

## 삭제

테스트가 끝나면 비용 방지를 위해 destroy한다.

```bash
terraform destroy
```

`skip_final_snapshot = true`이므로 기본값에서는 삭제 시 final snapshot을 만들지 않는다. 데이터를 보존해야 하는 테스트에서는 destroy 전에 snapshot 정책을 별도로 정한다.
