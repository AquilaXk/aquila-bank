"use client";

import { useState } from "react";
import type { CustomerApplicationType } from "@/lib/api/types";
import { securityHubItems } from "@/lib/customer-banking/constants";
import type {
  CustomerApplicationLatestResult,
  CustomerApplicationSubmitHandler,
} from "@/lib/customer-banking/types";
import { BankNoticeStrip, WorkTabs } from "../common";

function stayOnCurrentWorkTab(): void {}

type SecurityApplicationForm = {
  subject: string;
  serial: string;
  totpCode: string;
};

type SecurityMediaPayload = {
  mediaType: "OTP" | "SECURITY_CARD" | "MOBILE_OTP";
  deliveryMethod: "BRANCH" | "REGISTERED_MAIL";
};

type RegistrationFlow = {
  title: string;
  applicationType: CustomerApplicationType;
  description: string;
  steps: readonly string[];
  checks: readonly string[];
  initialSubject: string;
  initialSerial: string;
  securityMediaPayload?: SecurityMediaPayload;
};

const registrationFlows = [
  {
    title: "공동인증서 등록",
    applicationType: "CERTIFICATE_REGISTRATION" as CustomerApplicationType,
    description: "발급기관 DN과 인증서 일련번호 기준의 등록 신청 접수",
    steps: ["인증서 선택", "식별값 입력", "OTP 확인", "접수 대기"],
    checks: ["브라우저 저장소 저장 금지", "서명 원문 미보관", "만료일/발급기관 확인"],
    initialSubject: "CN=AQUILA_DEMO_CA",
    initialSerial: "CERT-20260514",
  },
  {
    title: "금융인증서 등록",
    applicationType: "CERTIFICATE_REGISTRATION" as CustomerApplicationType,
    description: "금융인증서 등록 요청을 식별값으로 접수",
    steps: ["본인확인", "식별값 입력", "OTP 확인", "접수 대기"],
    checks: ["요청번호 유지", "접수 시각 기록", "재등록 메뉴"],
    initialSubject: "CN=AQUILA_FINANCIAL_CERT",
    initialSerial: "FINCERT-20260514",
  },
  {
    title: "OTP 등록",
    applicationType: "SECURITY_MEDIA_APPLICATION" as CustomerApplicationType,
    description: "모바일 OTP 신청 정보를 접수하고 보안매체 적용 대기 상태로 표시합니다.",
    steps: ["매체 선택", "수령 방법 선택", "OTP 확인", "접수 대기"],
    checks: ["OTP 원문 저장 금지", "오류 횟수 안내", "분실 신고 연결"],
    initialSubject: "MOBILE_OTP",
    initialSerial: "BRANCH",
    securityMediaPayload: { mediaType: "MOBILE_OTP", deliveryMethod: "BRANCH" },
  },
  {
    title: "보안매체 등록",
    applicationType: "SECURITY_MEDIA_APPLICATION" as CustomerApplicationType,
    description: "보안카드와 모바일 OTP 발급 신청 접수",
    steps: ["매체 종류 선택", "수령 방법 선택", "OTP 확인", "접수 대기"],
    checks: ["등급별 한도 안내", "해지/재발급 분리", "고위험 업무 안내"],
    initialSubject: "SECURITY_CARD",
    initialSerial: "BRANCH",
    securityMediaPayload: { mediaType: "SECURITY_CARD", deliveryMethod: "BRANCH" },
  },
] as const satisfies readonly RegistrationFlow[];

function createSecurityApplicationPayload(
  flow: RegistrationFlow,
  form: SecurityApplicationForm,
): Record<string, unknown> {
  if (flow.applicationType === "CERTIFICATE_REGISTRATION") {
    return {
      certificateSerialNumber: form.serial,
      issuerDn: form.subject,
    };
  }
  return flow.securityMediaPayload ?? { mediaType: "MOBILE_OTP", deliveryMethod: "BRANCH" };
}

type SecurityHubSectionProps = {
  applicationResult: CustomerApplicationLatestResult;
  isBusy: boolean;
  onSubmitCustomerApplication: CustomerApplicationSubmitHandler;
};

export function SecurityHubSection({
  applicationResult,
  isBusy,
  onSubmitCustomerApplication,
}: SecurityHubSectionProps) {
  const [forms, setForms] = useState<Record<string, SecurityApplicationForm>>(
    () =>
      Object.fromEntries(
        registrationFlows.map((flow) => [
          flow.title,
          {
            subject: flow.initialSubject,
            serial: flow.initialSerial,
            totpCode: "",
          },
        ]),
      ),
  );

  function updateForm(title: string, key: "subject" | "serial" | "totpCode", value: string) {
    setForms((items) => ({
      ...items,
      [title]: {
        ...items[title],
        [key]: value,
      },
    }));
  }

  return (
    <section className="task-section compact-work-section">
      <div className="section-title">
        <div>
          <p>보안센터</p>
          <h1>인증서 · OTP · 보안매체</h1>
        </div>
      </div>
      <WorkTabs
        active="certificate"
        items={[
          { id: "certificate", label: "인증서", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "otp", label: "OTP", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "media", label: "보안매체", onClick: stayOnCurrentWorkTab, disabled: true },
        ]}
      />
      <BankNoticeStrip
        items={[
          { label: "공동인증서", value: "신청 접수" },
          { label: "금융인증서", value: "등록 접수" },
          { label: "OTP", value: "OTP 확인" },
          { label: "보안매체", value: "업무별 안내" },
        ]}
      />

      <div className="security-hub-grid">
        {securityHubItems.map((item) => (
          <article className="security-hub-card" key={item.title}>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <span>{item.status}</span>
          </article>
        ))}
      </div>

      <section className="table-panel">
        <div className="panel-toolbar">
          <div>
            <strong>보안매체별 MVP 적용 업무</strong>
            <span>이체/인증 신청 접수 기준</span>
          </div>
        </div>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>보안매체별 적용 업무</caption>
            <thead>
              <tr>
                <th>구분</th>
                <th>주요 업무</th>
                <th>확인 단계</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>공동인증서</td>
                <td>인증서 관리 접수</td>
                <td>식별값/OTP</td>
              </tr>
              <tr>
                <td>금융인증서</td>
                <td>로그인 보조, 등록 접수</td>
                <td>식별값/OTP</td>
              </tr>
              <tr>
                <td>OTP</td>
                <td>즉시이체, 한도 상향</td>
                <td>일회용 비밀번호</td>
              </tr>
              <tr>
                <td>보안매체</td>
                <td>보안카드, 모바일 OTP</td>
                <td>매체별 등급 확인</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="registration-flow-grid" aria-label="인증센터 등록 업무">
        {registrationFlows.map((flow) => (
          <article className="registration-flow-card" key={flow.title}>
            <div>
              <span>인증센터</span>
              <strong>{flow.title}</strong>
              <p>{flow.description}</p>
            </div>
            <ol className="process-strip" aria-label={`${flow.title} 단계`}>
              {flow.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
            <ul className="check-list">
              {flow.checks.map((check) => (
                <li key={check}>{check}</li>
              ))}
            </ul>
            <form
              className="application-submit-panel"
              onSubmit={(event) => {
                event.preventDefault();
                const form = forms[flow.title];
                void onSubmitCustomerApplication({
                  applicationType: flow.applicationType,
                  totpCode: form.totpCode,
                  payload: createSecurityApplicationPayload(flow, form),
                  successMessage: `${flow.title} 신청이 접수되었습니다.`,
                });
              }}
            >
              <div className="form-grid compact">
                <label>
                  <span>신청 대상</span>
                  <input
                    value={forms[flow.title].subject}
                    onChange={(event) => updateForm(flow.title, "subject", event.target.value)}
                  />
                </label>
                <label>
                  <span>일련번호/식별값</span>
                  <input
                    value={forms[flow.title].serial}
                    onChange={(event) => updateForm(flow.title, "serial", event.target.value)}
                  />
                </label>
                <label>
                  <span>OTP</span>
                  <input
                    inputMode="numeric"
                    value={forms[flow.title].totpCode}
                    onChange={(event) => updateForm(flow.title, "totpCode", event.target.value)}
                  />
                </label>
              </div>
              <button type="submit" disabled={isBusy}>
                등록 신청
              </button>
            </form>
          </article>
        ))}
      </section>
      {applicationResult ? (
        <p className="application-result-line">
          최근 보안 신청 {applicationResult.applicationReference} · {applicationResult.status}
        </p>
      ) : null}
    </section>
  );
}
