# AWS EC2 Legacy App Smoke Terraform

AWS에 App EC2 1대만 만드는 legacy/optional smoke stack이다. 현재 비용형 remote DB/capacity 기준은 OCI A1 Flex 4 OCPU / 24GB + data 300GB self-managed PostgreSQL이며, 이 AWS stack은 1억 건 DB baseline으로 사용하지 않는다.

## 구성

- EC2: `t3.small` legacy app smoke only
- EC2 root EBS: gp3, 40GiB
- Network: 새 VPC, public subnet 1개, Internet Gateway
- Security:
  - SSH `22/tcp`: `ssh_ingress_cidr`에서만 허용
  - HTTP `80/tcp`: `http_ingress_cidr`에서 허용
- IAM:
  - EC2 instance profile에 `AmazonSSMManagedInstanceCore`를 연결해 GitHub Actions가 SSM으로 배포 스크립트를 실행할 수 있게 한다.
- 제외: RDS, private DB subnet, NAT Gateway, ALB/NLB, Elastic IP

이 module은 로컬 Mac Docker PostgreSQL 또는 OCI A1 PostgreSQL에 있는 1억 건 fixture를 생성하거나 이동하지 않는다. EC2에서 외부 DB에 접속해야 하는 별도 실험은 SSH tunnel, VPN, allowlist 같은 별도 보안 설계가 필요하며 이번 Terraform 범위에서 제외한다.

## 비용 주의

- EC2 `t3.small`과 gp3 40GiB EBS는 free tier가 아닐 수 있다.
- 이 stack은 legacy app smoke 전용이다. remote 1억 건 DB/capacity 비교는 OCI A1 4 OCPU / 24GB + data 300GB stack을 사용한다.
- RDS를 만들지 않으므로 DB instance, RDS storage, snapshot 비용은 발생하지 않는다.
- 1억 건 검증 비용은 로컬 Mac Docker PostgreSQL + 로컬 디스크 사용량으로 제한한다.
- NAT Gateway와 Load Balancer는 고정 비용이 생기므로 기본 구성에서 제외한다.

## 준비 값

- AWS credentials: environment, shared config, SSO 중 하나
- 기존 EC2 key pair name
- 운영자 공인 IP `/32`
- HTTP 공개 CIDR. 개인 검증이면 좁게, 임시 공개 smoke면 `0.0.0.0/0`

## 실행

```bash
cd infra/terraform/aws/ec2-app-baseline
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에 실제 key pair와 SSH 허용 CIDR을 입력한다. 이 파일은 git에 커밋하지 않는다.

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

1억 건 DB primary evidence는 Mac Docker PostgreSQL에 남아 있고, 비용형 remote 비교는 OCI A1 PostgreSQL stack을 사용한다. 기본 local loadtest fixture 기준 접속 정보는 로컬에서 `localhost:15432`와 `aquila_bank`를 사용한다.

## 삭제

테스트가 끝나면 비용 방지를 위해 destroy한다.

```bash
terraform destroy
```
