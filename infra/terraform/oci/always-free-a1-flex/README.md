# OCI Always Free A1 Flex Terraform

OCI Always Free 한도 안에서 `VM.Standard.A1.Flex` 인스턴스 1대를 만든다.

## 구성

- Compute: `VM.Standard.A1.Flex`
- OCPU: `4`
- Memory: `24GB`
- Boot Volume: `50GB`
- Data Block Volume: `150GB`, Lower Cost `0` VPU/GB
- Network: 새 VCN, public subnet, Internet Gateway, Route Table
- Ingress: SSH `22/tcp`, staging HTTP `80/tcp`, staging HTTPS `443/tcp`
- 제외: NAT Gateway, Load Balancer, Managed Database

## 무료 한도 조건

- A1 Flex Always Free 한도는 총 4 OCPU / 24GB RAM 범위다.
- Block Volume Always Free 한도는 홈 리전의 boot volume과 data block volume 합산 200GB다.
- 이 스택은 Boot Volume 50GB와 data Block Volume 150GB를 생성한다.
- data Block Volume만 Lower Cost `0` VPU/GB로 설정한다. Boot Volume은 OCI 정책상 `0` VPU를 적용할 수 없어 기본 Balanced 성능으로 유지된다.
- 기존 200GB boot volume 단일 구성에서 이 구조로 바꾸면 boot volume shrink가 아니라 instance 재생성으로 처리될 수 있다. `terraform plan`에서 destroy/create 범위를 먼저 확인한다.
- `region`은 tenancy 홈 리전으로 설정한다. 홈 리전 밖 volume은 무료 한도 적용에서 벗어날 수 있다.
- 기본 경로는 Terraform `oci_core_images` data source로 `VM.Standard.A1.Flex` 호환 최신 Ubuntu 이미지를 조회한다.
- 최신 이미지 자동 조회는 신규 생성에만 사용한다. 기존 instance는 `source_id` drift를 무시해 보안목록 같은 운영 변경에 이미지 변경이 섞이지 않게 한다.
- 재현성이 필요하면 `source_image_ocid_override`에 특정 image OCID를 고정한다.
- cloud-init은 data volume을 `/var/lib/aquila-data`에 mount하고 Docker data-root를 `/var/lib/aquila-data/docker`로 고정한다. staging deploy의 Docker named volume과 image layer는 이 data volume을 사용한다.

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
- `http_ingress_cidr`
- `https_ingress_cidr`

선택 값:

- `image_operating_system`: 기본값 `Canonical Ubuntu`
- `image_operating_system_version`: 기본값 `null`
- `source_image_ocid_override`: 기본값 `null`
- `data_volume_device`: 기본값 `/dev/oracleoci/oraclevdb`
- `data_volume_mount_path`: 기본값 `/var/lib/aquila-data`

`ssh_ingress_cidr`는 운영자 현재 공인 IP의 `/32`를 우선 사용한다. `0.0.0.0/0`은 임시 테스트가 아니면 사용하지 않는다.
`http_ingress_cidr`는 GitHub Actions smoke와 브라우저 접근을 받는 staging HTTP 포트다. public staging이면 `0.0.0.0/0`, 사설 접근만 허용할 수 있으면 제한 CIDR을 사용한다.
`https_ingress_cidr`는 공인 인증서 기반 HTTPS 포트다. public staging이면 `0.0.0.0/0`, Cloudflare Tunnel/사설 접근만 사용할 수 있으면 제한 CIDR을 사용한다.

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
또한 `data_volume_id`, `data_volume_attachment_id`, `data_volume_mount_path`를 확인한다.

배포 전 확인:

```bash
ssh ubuntu@<instance_public_ip> 'df -h /var/lib/aquila-data && cat /etc/docker/daemon.json'
```

GitHub Actions staging deploy는 기본 storage gate를 `/var/lib/aquila-data`와 `140GiB`로 확인한다. staging secret에서 `OCI_A1_STORAGE_MOUNT_PATH`나 `OCI_A1_STORAGE_MIN_USABLE_GIB`를 별도로 지정했다면 새 mount 기준과 맞춰 갱신한다.

## 삭제

```bash
terraform destroy
```

`preserve_boot_volume = false`이므로 destroy 시 boot volume도 함께 제거된다. Terraform 관리 대상 data Block Volume도 함께 삭제된다. 수동으로 만든 volume backup이나 추가 volume은 별도로 확인한다.

## 용량 부족 대응

OCI A1 Always Free는 리전/AD별 capacity 부족으로 생성이 실패할 수 있다. 이 경우 `availability_domain`을 같은 홈 리전의 다른 AD로 바꾸거나, 시간을 두고 다시 `terraform apply`를 실행한다.
