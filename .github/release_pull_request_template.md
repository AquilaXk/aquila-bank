## 🔄 Release Summary
- source branch: `develop`
- target branch: `main`
- release unit: deployable bundle only

## ✅ Release Conditions
- [x] latest `develop` commit passed backend/frontend release gate
- [x] `develop` is ahead of `main`
- [x] release scope is limited to deployable changes only
- [ ] production smoke check after merge

## 🧪 Validation
- Backend Release Check
- Frontend Release Check

## ↩️ Rollback
- revert the release merge commit on `main` when rollback is needed
