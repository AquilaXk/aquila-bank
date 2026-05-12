"use client";

import { useState } from "react";
import type { CustomerApplicationType } from "@/lib/api/types";
import { enterpriseServiceItems } from "@/lib/customer-banking/constants";
import type {
  CustomerApplicationLatestResult,
  CustomerApplicationSubmitHandler,
} from "@/lib/customer-banking/types";
import {
  BankActionBar,
  BankInput,
  BankNoticeStrip,
  BankTable,
  WorkTabs,
} from "../common";

function stayOnCurrentWorkTab(): void {}

const fulfillmentDetails = [
  {
    title: "공과금 납부",
    applicationType: "BILL_PAYMENT",
    headline: "지로/지방세/아파트관리비 납부",
    description: "기관, 납부번호, 출금계좌, OTP 기준의 접수 업무",
    fields: ["납부기관", "전자납부번호", "출금계좌", "납부예정일"],
    formFields: [
      { key: "billerCode", label: "납부기관", value: "GIRO" },
      { key: "paymentNumber", label: "전자납부번호", value: "1234567890" },
      { key: "dueDate", label: "납부예정일", value: "2026-05-20" },
    ],
    steps: ["기관 선택", "납부번호 입력", "출금계좌 입력", "접수 완료"],
    status: "OTP 확인 후 접수",
  },
  {
    title: "오픈뱅킹 연결",
    applicationType: "OPEN_BANKING_CONNECTION",
    headline: "타행 계좌 연결 신청",
    description: "동의 만료일, 연결 은행, 계좌 별칭 기준의 접수 업무",
    fields: ["은행", "계좌 별칭", "동의 만료일", "최근 동기화"],
    formFields: [
      { key: "bankCode", label: "은행코드", value: "088" },
      { key: "accountAlias", label: "계좌 별칭", value: "급여 타행" },
      { key: "consentExpiresAt", label: "동의 만료일", value: "2027-05-11" },
    ],
    steps: ["은행 선택", "동의 정보 입력", "OTP 확인", "접수 완료"],
    status: "OTP 확인 후 연결 신청",
  },
  {
    title: "예금 가입",
    applicationType: "DEPOSIT_PRODUCT_APPLICATION",
    headline: "정기예금/입출금 상품 가입 신청",
    description: "상품코드, 가입기간, 가입금액, OTP 기준의 접수 업무",
    fields: ["상품코드", "가입기간", "가입금액", "출금계좌"],
    formFields: [
      { key: "productCode", label: "상품코드", value: "DEP-12M" },
      { key: "termMonths", label: "가입기간", value: "12" },
      { key: "amountMinor", label: "가입금액", value: "1000000" },
    ],
    steps: ["상품 선택", "가입정보 입력", "OTP 확인", "접수 완료"],
    status: "OTP 확인 후 가입 신청",
  },
  {
    title: "대출 신청",
    applicationType: "LOAN_APPLICATION",
    headline: "대출 한도 신청/서류 안내",
    description: "대출 유형, 희망 한도, 상환 방식 기준의 접수 업무",
    fields: ["대출 유형", "희망 한도", "상환 방식", "필요 서류"],
    formFields: [
      { key: "loanType", label: "대출 유형", value: "credit" },
      { key: "requestedLimitMinor", label: "희망 한도", value: "10000000" },
      { key: "repaymentType", label: "상환 방식", value: "amortized" },
    ],
    steps: ["기본정보 확인", "희망 한도 입력", "서류 안내", "접수 완료"],
    status: "OTP 확인 후 한도 신청",
  },
  {
    title: "외환 신청",
    applicationType: "FOREIGN_EXCHANGE_APPLICATION",
    headline: "외화예금/해외송금 신청 접수",
    description: "통화, 신청 목적, 우대 조건 기준의 접수 업무",
    fields: ["통화", "신청 목적", "우대 조건", "출금계좌"],
    formFields: [
      { key: "currencyCode", label: "통화", value: "USD" },
      { key: "purpose", label: "신청 목적", value: "foreign-deposit" },
      { key: "preferredRateCode", label: "우대 조건", value: "WEB" },
    ],
    steps: ["통화 선택", "신청 목적 입력", "OTP 확인", "접수 완료"],
    status: "OTP 확인 후 외환 신청",
  },
] as const satisfies ReadonlyArray<{
  title: string;
  applicationType: CustomerApplicationType;
  headline: string;
  description: string;
  fields: readonly string[];
  formFields: readonly { key: string; label: string; value: string }[];
  steps: readonly string[];
  status: string;
}>;

type FulfillmentApplicationType = (typeof fulfillmentDetails)[number]["applicationType"];

type EnterpriseServicesSectionProps = {
  applicationResult: CustomerApplicationLatestResult;
  isBusy: boolean;
  onSubmitCustomerApplication: CustomerApplicationSubmitHandler;
};

type ApplicationFormState = Record<string, string>;

function createInitialForms(): Record<FulfillmentApplicationType, ApplicationFormState> {
  const initialForms = {} as Record<FulfillmentApplicationType, ApplicationFormState>;
  fulfillmentDetails.forEach((item) => {
    initialForms[item.applicationType] = {
      accountId: "",
      totpCode: "",
      ...Object.fromEntries(item.formFields.map((field) => [field.key, field.value])),
    };
  });
  return initialForms;
}

export function EnterpriseServicesSection({
  applicationResult,
  isBusy,
  onSubmitCustomerApplication,
}: EnterpriseServicesSectionProps) {
  const [forms, setForms] =
    useState<Record<FulfillmentApplicationType, ApplicationFormState>>(createInitialForms);

  function updateForm(applicationType: FulfillmentApplicationType, key: string, value: string) {
    setForms((items) => ({
      ...items,
      [applicationType]: {
        ...items[applicationType],
        [key]: value,
      },
    }));
  }

  return (
    <section className="task-section compact-work-section">
      <div className="section-title">
        <div>
          <p>부가업무</p>
          <h1>공과금 · 오픈뱅킹 · 금융상품</h1>
        </div>
      </div>
      <WorkTabs
        active="bill"
        items={[
          { id: "bill", label: "공과금", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "open-banking", label: "오픈뱅킹", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "products", label: "금융상품", onClick: stayOnCurrentWorkTab, disabled: true },
        ]}
      />
      <BankNoticeStrip
        items={[
          { label: "공과금", value: "납부 접수" },
          { label: "오픈뱅킹", value: "연결 신청" },
          { label: "예금상품", value: "가입 신청" },
          { label: "대출", value: "한도 신청" },
          { label: "외환", value: "외환 신청" },
        ]}
      />

      <div className="enterprise-service-grid">
        {enterpriseServiceItems.map((item) => (
          <article className="enterprise-service-card" key={item.title}>
            <span>{item.category}</span>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <small>{item.status}</small>
          </article>
        ))}
      </div>

      <BankTable caption="부가업무 처리 기준" className="dense-bank-table">
          <thead>
            <tr>
              <th>업무</th>
              <th>상용 화면 기준</th>
              <th>현재 범위</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>공과금</td>
              <td>기관, 납부번호, 출금계좌, OTP</td>
              <td>접수 가능</td>
            </tr>
            <tr>
              <td>오픈뱅킹</td>
              <td>타행 계좌 연결 신청</td>
              <td>연결 신청</td>
            </tr>
            <tr>
              <td>예금상품</td>
              <td>상품코드, 가입기간, 가입금액, OTP</td>
              <td>가입 신청</td>
            </tr>
            <tr>
              <td>대출</td>
              <td>대출 유형, 희망 한도, 상환 방식, OTP</td>
              <td>한도 신청</td>
            </tr>
            <tr>
              <td>외환</td>
              <td>외환 신청 접수</td>
              <td>외환 신청</td>
            </tr>
          </tbody>
      </BankTable>

      <section className="fulfillment-detail-grid" aria-label="부가업무 상세 화면">
        {fulfillmentDetails.map((item) => (
          <article className="fulfillment-detail-card" key={item.title}>
            <div className="fulfillment-detail-head">
              <span>{item.title}</span>
              <strong>{item.headline}</strong>
              <p>{item.description}</p>
            </div>
            <dl className="detail-list">
              <div>
                <dt>필수 입력</dt>
                <dd>{item.fields.join(" / ")}</dd>
              </div>
              <div>
                <dt>현재 상태</dt>
                <dd>{item.status}</dd>
              </div>
            </dl>
            <ol className="process-strip" aria-label={`${item.title} 처리 단계`}>
              {item.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
            <form
              className="application-submit-panel enterprise-application-form"
              onSubmit={(event) => {
                event.preventDefault();
                const form = forms[item.applicationType];
                void onSubmitCustomerApplication({
                  applicationType: item.applicationType,
                  accountId: form.accountId,
                  totpCode: form.totpCode,
                  payload: Object.fromEntries(
                    item.formFields.map((field) => [field.key, form[field.key]]),
                  ),
                  successMessage: `${item.title} 접수가 완료되었습니다.`,
                });
              }}
            >
              <div className="form-grid compact">
                <BankInput
                  inputMode="numeric"
                  label="계좌 ID"
                  onChange={(event) =>
                    updateForm(item.applicationType, "accountId", event.target.value)
                  }
                  placeholder="선택"
                  value={forms[item.applicationType].accountId}
                />
                {item.formFields.map((field) => (
                  <BankInput
                    key={field.key}
                    label={field.label}
                    onChange={(event) =>
                      updateForm(item.applicationType, field.key, event.target.value)
                    }
                    value={forms[item.applicationType][field.key]}
                  />
                ))}
                <BankInput
                  inputMode="numeric"
                  label="OTP"
                  onChange={(event) =>
                    updateForm(item.applicationType, "totpCode", event.target.value)
                  }
                  value={forms[item.applicationType].totpCode}
                />
              </div>
              <BankActionBar>
                <button type="submit" disabled={isBusy}>
                  신청 접수
                </button>
              </BankActionBar>
            </form>
          </article>
        ))}
      </section>
      {applicationResult ? (
        <p className="application-result-line">
          최근 접수 {applicationResult.applicationReference} · {applicationResult.status}
        </p>
      ) : null}
    </section>
  );
}
