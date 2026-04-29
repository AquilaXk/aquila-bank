# OCI Paid A1 PostgreSQL Terraform

OCI 유료 계정에서 `VM.Standard.A1.Flex` 단일 VM과 self-managed PostgreSQL 18용 Block Volume을 만든다.

## 구성

- Compute: `VM.Standard.A1.Flex`
- Size: `4 OCPU / 24GB`
- Boot Volume: `50GB`
- PostgreSQL data volume: `300GB`, Balanced `10` VPUs
- Network: 새 VCN, public subnet, Internet Gateway, Route Table
- Ingress: SSH `22/tcp`, 선택 HTTP `80/tcp`, 선택 HTTPS `443/tcp`
- PostgreSQL: Docker `PostgreSQL 18`, host `127.0.0.1:5432` 바인딩, app network `aquila-bank-prod`
- 100m fixture: `/var/lib/aquila-postgres/data`가 1억 건 transaction read model primary evidence 저장소
- 제외: Managed Database, NAT Gateway, Load Balancer, PostgreSQL public ingress

## 보안 계약

PostgreSQL 5432는 OCI security list에서 열지 않는다. 외부 접속은 SSH tunnel만 사용한다.

```bash
ssh -L 5432:127.0.0.1:5432 ubuntu@<instance_public_ip>
```

`ssh_ingress_cidr`는 운영자 현재 공인 IP `/32`를 사용한다. `http_ingress_cidr`, `https_ingress_cidr`는 앱 smoke가 필요할 때만 설정하고 기본값 `null`을 유지하면 닫힌다.

## 실행

```bash
cd infra/terraform/oci/paid-a1-postgres
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에 실제 OCID, fingerprint, SSH key, CIDR 값을 입력한다. 이 파일은 git에 커밋하지 않는다.

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

## PostgreSQL 18 시작

cloud-init은 Docker와 data volume mount 서비스를 설치한다. PostgreSQL container는 기본 비밀번호를 막기 위해 자동 시작하지 않는다.

```bash
sudo vi /etc/aquila-postgres.env
```

`AQUILA_POSTGRES_PASSWORD=change-me-before-running`을 실제 값으로 바꾼 뒤 시작한다.

```bash
sudo systemctl enable --now aquila-postgres.service
sudo systemctl status aquila-postgres.service
```

데이터 경로:

- mount: `/var/lib/aquila-postgres`
- PostgreSQL data: `/var/lib/aquila-postgres/data`
- container port: `127.0.0.1:5432:5432`
- app container DNS: `aquila-postgres:5432`

1억 건 fixture도 같은 data volume에 적재합니다. fixture 생성/restore/k6 실행 전에는 `df -h /var/lib/aquila-postgres`로 여유 공간을 확인하고, DB 접속은 public ingress 대신 SSH tunnel 또는 private 경로만 사용합니다.

## 비용 경계

이 스택은 유료 baseline이다. A1 4 OCPU / 24GB는 Always Free 범위와 겹칠 수 있지만, 300GB data volume은 200GB Always Free Block Volume 총량을 넘으므로 과금될 수 있다. 비용은 주로 Block Volume storage와 Balanced VPU에서 발생한다.

저비용 기본값:

- `postgres_data_volume_size_in_gbs = 300`
- `postgres_data_volume_vpus_per_gb = 10`

IO 병목 검증이 필요할 때만 `20` 이상으로 올린다.

## 삭제

```bash
terraform destroy
```

`preserve_boot_volume = false`이고 data Block Volume도 Terraform 관리 대상이므로 destroy 시 함께 삭제된다. 별도로 만든 backup, snapshot, object storage export는 OCI Console에서 따로 확인한다.
