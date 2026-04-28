# OCI Always Free A1 Flex Terraform Design

## Goal
- OCI Always Free 한도 안에서 `VM.Standard.A1.Flex` 단일 인스턴스를 Terraform으로 생성한다.
- 목표 스펙은 4 OCPU, 24GB RAM, Boot Volume 150GB이다.

## Selected Approach
- 새 OCI VCN을 만들고, public subnet에 A1 Flex VM 1대를 배치한다.
- Internet Gateway와 route table은 public SSH 접속에 필요한 최소 구성만 둔다.
- Security List ingress는 SSH 22번만 허용하고, 허용 CIDR은 변수로 받는다.
- NAT Gateway, Load Balancer, Database, 추가 Block Volume은 만들지 않는다.

## Free Tier Boundary
- Oracle 공식 Always Free 문서 기준 A1 Flex는 월 3,000 OCPU hours / 18,000 GB hours이며 Always Free 전용 계정에서는 4 OCPU / 24GB RAM에 해당한다.
- Oracle 공식 Always Free 문서 기준 Block Volume은 홈 리전에서 boot volume과 block volume 합산 200GB이다.
- 이번 구성은 Boot Volume 150GB만 생성하므로, 기존 OCI boot/block volume 합산 사용량이 50GB 이하일 때 무료 한도 안에 머문다.
- Always Free volume은 홈 리전 전제가 중요하므로 `region`은 tenancy 홈 리전으로 설정해야 한다.

## Terraform Structure
- 경로: `infra/terraform/oci/always-free-a1-flex`
- Provider: `oracle/oci`
- 주요 파일:
  - `versions.tf`: Terraform/provider 버전 제약
  - `providers.tf`: OCI provider 인증 변수 연결
  - `variables.tf`: OCID, region, SSH key, CIDR, 무료 한도 guardrail 변수
  - `locals.tf`: shape, size, 공통 tag
  - `network.tf`: VCN, Internet Gateway, Route Table, Security List, Subnet
  - `compute.tf`: A1 Flex instance
  - `outputs.tf`: instance, VCN, public IP 출력
  - `terraform.tfvars.example`: 실값 없는 예시
  - `README.md`: 실행 순서와 과금 방지 주의사항

## Inputs
- `tenancy_ocid`, `user_ocid`, `fingerprint`, `private_key_path`, `region`
- `compartment_ocid`, `availability_domain`, `source_image_ocid`
- `ssh_public_key`
- `ssh_ingress_cidr`
- `vcn_cidr`, `subnet_cidr`

## Error And Risk Handling
- A1 host capacity 부족은 Terraform 코드로 해결할 수 없으므로 apply 실패 시 availability domain 변경 또는 재시도 안내로 처리한다.
- 이미지 OCID는 리전별로 다르므로 자동 추정하지 않고 사용자가 Always Free eligible Arm image OCID를 명시한다.
- `ssh_ingress_cidr` 기본 예시는 넓게 열 수 있지만 README에서 운영자 IP `/32` 사용을 우선 안내한다.
- Terraform state와 tfvars에는 민감 정보가 포함될 수 있으므로 `.gitignore`에 state/tfvars/plan 파일을 제외한다.

## Validation
- `terraform fmt -check`
- `terraform init -backend=false`
- `terraform validate`
- 실제 `apply`는 OCI 계정/홈 리전/용량/이미지 OCID가 필요하므로 로컬 자동 검증 범위에서 제외한다.
