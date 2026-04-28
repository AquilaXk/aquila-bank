# OCI Always Free A1 Flex Terraform

OCI Always Free 한도 안에서 `VM.Standard.A1.Flex` 인스턴스 1대를 만든다.

## 구성

- Compute: `VM.Standard.A1.Flex`
- OCPU: `4`
- Memory: `24GB`
- Boot Volume: `150GB`
- Network: 새 VCN, public subnet, Internet Gateway, Route Table
- Ingress: SSH `22/tcp`만 허용
- 제외: NAT Gateway, Load Balancer, Database, 추가 Block Volume

## 무료 한도 조건

- A1 Flex Always Free 한도는 총 4 OCPU / 24GB RAM 범위다.
- Block Volume Always Free 한도는 홈 리전의 boot volume과 block volume 합산 200GB다.
- 이 스택은 Boot Volume 150GB를 생성한다. 기존 OCI boot/block volume 합산 사용량이 50GB를 넘으면 무료 한도 초과 가능성이 있다.
- `region`은 tenancy 홈 리전으로 설정한다. 홈 리전 밖 volume은 무료 한도 적용에서 벗어날 수 있다.
- 기본 경로는 Terraform `oci_core_images` data source로 `VM.Standard.A1.Flex` 호환 최신 Ubuntu 이미지를 조회한다.
- 최신 이미지 자동 조회는 다음 `terraform apply` 시점에 더 새 이미지가 잡힐 수 있다. 재현성이 필요하면 `source_image_ocid_override`에 특정 image OCID를 고정한다.

## 준비 값

- `tenancy_ocid`
- `user_ocid`
- `fingerprint`
- `private_key_path`
- `region`
- `compartment_ocid`
- `availability_domain`
- `ssh_public_key`
- `ssh_ingress_cidr`

선택 값:

- `image_operating_system`: 기본값 `Canonical Ubuntu`
- `image_operating_system_version`: 기본값 `null`
- `source_image_ocid_override`: 기본값 `null`

`ssh_ingress_cidr`는 운영자 현재 공인 IP의 `/32`를 우선 사용한다. `0.0.0.0/0`은 임시 테스트가 아니면 사용하지 않는다.

## 실행

```bash
cd infra/terraform/oci/always-free-a1-flex
cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`에 실제 값을 입력한다. 이 파일은 git에 커밋하지 않는다.

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan
terraform apply
```

`terraform apply` 후 output의 `selected_image_display_name`과 `selected_image_id`를 확인한다. 같은 이미지로 계속 재현해야 하면 해당 `selected_image_id`를 `source_image_ocid_override`에 넣고 다시 plan을 확인한다.

## 삭제

```bash
terraform destroy
```

`preserve_boot_volume = false`이므로 destroy 시 boot volume도 함께 제거된다. 수동으로 만든 volume backup이나 추가 volume은 별도로 확인한다.

## 용량 부족 대응

OCI A1 Always Free는 리전/AD별 capacity 부족으로 생성이 실패할 수 있다. 이 경우 `availability_domain`을 같은 홈 리전의 다른 AD로 바꾸거나, 시간을 두고 다시 `terraform apply`를 실행한다.
