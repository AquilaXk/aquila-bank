"use client";

import { useState } from "react";
import type { CustomerApplicationDetailsResponse } from "@/lib/api/types";
import { supportCenterItems } from "@/lib/customer-banking/constants";
import { formatDateTime } from "@/lib/customer-banking/format";
import type {
  CustomerApplicationLatestResult,
  CustomerApplicationSubmitHandler,
} from "@/lib/customer-banking/types";
import { BankNoticeStrip, WorkTabs } from "../common";

function stayOnCurrentWorkTab(): void {}

const faqItems = [
  {
    question: "FAQ 이체확인증은 어디서 발급하나요?",
    answer: "이체 완료 후 완료증 영역에서 거래번호를 확인하고 증명서 메뉴의 이체확인증 발급으로 이동합니다.",
  },
  {
    question: "OTP 오류 횟수가 초과되면 어떻게 하나요?",
    answer: "사고신고 접수에서 보안매체 분실/오류 유형을 선택하고 재등록 신청을 접수합니다.",
  },
  {
    question: "오픈뱅킹 연결 계좌가 보이지 않습니다.",
    answer: "동의 식별값과 은행 점검 시간을 확인한 뒤 연결 신청을 다시 접수합니다.",
  },
];

const certificateItems = [
  {
    title: "이체확인증",
    description: "거래번호, 출금계좌, 입금계좌, 이체금액, 수수료, 처리시각",
    scope: "즉시이체 완료 거래",
    subjectCode: "TRANSFER_RECEIPT",
  },
  {
    title: "잔액증명서",
    description: "기준일, 계좌, 통화, 잔액, 발급 목적 기준의 신청 접수",
    scope: "예금/입출금 계좌",
    subjectCode: "BALANCE_CERTIFICATE",
  },
  {
    title: "거래내역확인서",
    description: "조회 기간, 거래 상태, 입출금 구분, 증명 대상 거래",
    scope: "거래내역 조회 결과",
    subjectCode: "TRANSACTION_CERTIFICATE",
  },
] as const;

type SupportCenterSectionProps = {
  applicationResult: CustomerApplicationLatestResult;
  customerApplications: CustomerApplicationDetailsResponse[];
  isBusy: boolean;
  selectedCustomerApplication: CustomerApplicationDetailsResponse | null;
  onCancelCustomerApplication: (applicationReference: string) => void | Promise<void>;
  onLoadCustomerApplications: () => void | Promise<void>;
  onSelectCustomerApplication: (applicationReference: string) => void | Promise<void>;
  onSubmitCustomerApplication: CustomerApplicationSubmitHandler;
};

export function SupportCenterSection({
  applicationResult,
  customerApplications,
  isBusy,
  selectedCustomerApplication,
  onCancelCustomerApplication,
  onLoadCustomerApplications,
  onSelectCustomerApplication,
  onSubmitCustomerApplication,
}: SupportCenterSectionProps) {
  const [incidentForm, setIncidentForm] = useState({
    incidentType: "SECURITY_MEDIA_LOSS",
    accountId: "",
    target: "",
    contact: "",
    totpCode: "",
  });
  const [certificateForms, setCertificateForms] = useState(() =>
    Object.fromEntries(
      certificateItems.map((item) => [
        item.title,
        {
          accountId: "",
          purpose: "제출용",
          totpCode: "",
        },
      ]),
    ),
  );

  function updateCertificateForm(title: string, key: string, value: string) {
    setCertificateForms((items) => ({
      ...items,
      [title]: {
        ...items[title],
        [key]: value,
      },
    }));
  }

  function formatExecutionResult(result: Record<string, unknown>): string {
    return Object.keys(result).length === 0 ? "없음" : JSON.stringify(result, null, 2);
  }

  return (
    <section className="task-section compact-work-section">
      <div className="section-title">
        <div>
          <p>고객센터</p>
          <h1>고객지원 · 사고신고 · 이체한도</h1>
        </div>
      </div>
      <WorkTabs
        active="support"
        items={[
          { id: "support", label: "고객센터", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "incident", label: "사고신고", onClick: stayOnCurrentWorkTab, disabled: true },
          { id: "certificate", label: "증명서", onClick: stayOnCurrentWorkTab, disabled: true },
        ]}
      />
      <BankNoticeStrip
        items={[
          { label: "FAQ", value: "자주 찾는 문의" },
          { label: "사고신고", value: "분실/도용 접수" },
          { label: "이체한도", value: "변경 접수" },
          { label: "증명서 발급", value: "접수 데모" },
        ]}
      />

      <div className="support-service-grid">
        {supportCenterItems.map((item) => (
          <article className="support-service-card" key={item.title}>
            <strong>{item.title}</strong>
            <p>{item.description}</p>
            <button type="button">{item.action}</button>
          </article>
        ))}
      </div>

      <section className="table-panel">
        <div className="panel-toolbar">
          <div>
            <strong>사고신고 우선순위</strong>
            <span>분실/도용 의심 업무 우선 접수</span>
          </div>
        </div>
        <div className="bank-table-wrap">
          <table className="bank-table">
            <caption>사고신고 우선순위</caption>
            <thead>
              <tr>
                <th>신고 유형</th>
                <th>대상</th>
                <th>초기 조치</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>보안매체 분실</td>
                <td>OTP, 보안카드</td>
                <td>접수 후 운영자 확인</td>
              </tr>
              <tr>
                <td>인증서 도용 의심</td>
                <td>공동인증서, 금융인증서</td>
                <td>접수 후 재등록 안내</td>
              </tr>
              <tr>
                <td>이체한도 관리</td>
                <td>1회/1일 한도</td>
                <td>보안등급 확인</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section className="support-detail-grid" aria-label="고객센터 상세 업무">
        <article className="application-status-panel">
          <div className="panel-toolbar">
            <div>
              <strong>신청 현황</strong>
              <span>접수/심사/mock/webhook 경계 상태</span>
            </div>
            <button
              type="button"
              disabled={isBusy}
              onClick={() => void onLoadCustomerApplications()}
            >
              신청 목록 새로고침
            </button>
          </div>
          {customerApplications.length === 0 ? (
            <p className="empty-state">조회된 신청이 없습니다.</p>
          ) : (
            <ul className="application-status-list">
              {customerApplications.map((item) => (
                <li key={item.applicationReference}>
                  <button
                    type="button"
                    onClick={() =>
                      void onSelectCustomerApplication(item.applicationReference)
                    }
                  >
                    <strong>{item.applicationReference}</strong>
                    <span>{item.applicationType}</span>
                  </button>
                  <span>{item.status}</span>
                  <button
                    type="button"
                    disabled={isBusy}
                    onClick={() =>
                      void onCancelCustomerApplication(item.applicationReference)
                    }
                  >
                    취소 요청
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="application-detail-card">
            <div className="panel-toolbar compact">
              <div>
                <strong>신청 상세</strong>
                <span>선택한 신청의 처리 상태</span>
              </div>
            </div>
            {selectedCustomerApplication ? (
              <>
                <dl className="application-detail-list">
                  <div>
                    <dt>접수번호</dt>
                    <dd>{selectedCustomerApplication.applicationReference}</dd>
                  </div>
                  <div>
                    <dt>상태</dt>
                    <dd>{selectedCustomerApplication.status}</dd>
                  </div>
                  <div>
                    <dt>처리 모드</dt>
                    <dd>{selectedCustomerApplication.processingMode}</dd>
                  </div>
                  <div>
                    <dt>사유</dt>
                    <dd>{selectedCustomerApplication.reason ?? "없음"}</dd>
                  </div>
                  <div>
                    <dt>처리자</dt>
                    <dd>{selectedCustomerApplication.processedBy ?? "미처리"}</dd>
                  </div>
                  <div>
                    <dt>처리 시각</dt>
                    <dd>{formatDateTime(selectedCustomerApplication.processedAt)}</dd>
                  </div>
                </dl>
                <div className="execution-result-box">
                  <strong>실행 결과</strong>
                  <pre>
                    {formatExecutionResult(selectedCustomerApplication.executionResult)}
                  </pre>
                </div>
              </>
            ) : (
              <p className="empty-state">신청을 선택하면 상세 상태가 표시됩니다.</p>
            )}
          </div>
        </article>

        <article className="faq-list">
          <div className="panel-toolbar">
            <div>
              <strong>FAQ</strong>
              <span>자주 찾는 문의</span>
            </div>
          </div>
          {faqItems.map((item) => (
            <details key={item.question}>
              <summary>{item.question}</summary>
              <p>{item.answer}</p>
            </details>
          ))}
        </article>

        <article className="incident-form">
          <div className="panel-toolbar">
            <div>
              <strong>사고신고 접수</strong>
              <span>분실/도용/오류 신고</span>
            </div>
          </div>
          <div className="form-grid">
            <label>
              <span>신고 유형</span>
              <select
                value={incidentForm.incidentType}
                onChange={(event) =>
                  setIncidentForm((form) => ({ ...form, incidentType: event.target.value }))
                }
              >
                <option value="SECURITY_MEDIA_LOSS">보안매체 분실</option>
                <option value="CERTIFICATE_LEAK">인증서 도용 의심</option>
                <option value="ACCOUNT_FRAUD">미확인 이체</option>
                <option value="CARD_LOSS">카드 분실</option>
              </select>
            </label>
            <label>
              <span>대상 계좌/매체</span>
              <input
                value={incidentForm.target}
                onChange={(event) =>
                  setIncidentForm((form) => ({ ...form, target: event.target.value }))
                }
              />
            </label>
            <label>
              <span>긴급 연락처</span>
              <input
                value={incidentForm.contact}
                onChange={(event) =>
                  setIncidentForm((form) => ({ ...form, contact: event.target.value }))
                }
              />
            </label>
            <label>
              <span>계좌 ID</span>
              <input
                inputMode="numeric"
                value={incidentForm.accountId}
                onChange={(event) =>
                  setIncidentForm((form) => ({ ...form, accountId: event.target.value }))
                }
              />
            </label>
            <label>
              <span>OTP</span>
              <input
                inputMode="numeric"
                value={incidentForm.totpCode}
                onChange={(event) =>
                  setIncidentForm((form) => ({ ...form, totpCode: event.target.value }))
                }
              />
            </label>
          </div>
          <button
            type="button"
            disabled={isBusy}
            onClick={() =>
              void onSubmitCustomerApplication({
                applicationType: "INCIDENT_REPORT",
                accountId: incidentForm.accountId,
                totpCode: incidentForm.totpCode,
                payload: {
                  incidentType: incidentForm.incidentType,
                  description: `대상: ${incidentForm.target || "미입력"} / 연락처: ${
                    incidentForm.contact || "미입력"
                  }`,
                },
                successMessage: "사고신고가 접수되었습니다.",
              })
            }
          >
            신고 접수
          </button>
        </article>

        <article className="certificate-list">
          <div className="panel-toolbar">
            <div>
              <strong>증명서 발급</strong>
              <span>확인증, 잔액, 거래내역</span>
            </div>
          </div>
          {certificateItems.map((item) => (
            <div className="certificate-item" key={item.title}>
              <strong>{item.title}</strong>
              <p>{item.description}</p>
              <span>{item.scope}</span>
              <div className="form-grid compact">
                <label>
                  <span>계좌 ID</span>
                  <input
                    inputMode="numeric"
                    value={certificateForms[item.title].accountId}
                    onChange={(event) =>
                      updateCertificateForm(item.title, "accountId", event.target.value)
                    }
                  />
                </label>
                <label>
                  <span>발급 목적</span>
                  <input
                    value={certificateForms[item.title].purpose}
                    onChange={(event) =>
                      updateCertificateForm(item.title, "purpose", event.target.value)
                    }
                  />
                </label>
                <label>
                  <span>OTP</span>
                  <input
                    inputMode="numeric"
                    value={certificateForms[item.title].totpCode}
                    onChange={(event) =>
                      updateCertificateForm(item.title, "totpCode", event.target.value)
                    }
                  />
                </label>
              </div>
              <button
                type="button"
                disabled={isBusy}
                onClick={() =>
                  void onSubmitCustomerApplication({
                    applicationType: "CERTIFICATE_ISSUANCE",
                    accountId: certificateForms[item.title].accountId,
                    totpCode: certificateForms[item.title].totpCode,
                    payload: {
                      certificateType: "BANKING",
                      subjectDn: `CN=${item.subjectCode};PURPOSE=${
                        certificateForms[item.title].purpose
                      }`,
                    },
                    successMessage: `${item.title} 발급이 접수되었습니다.`,
                  })
                }
              >
                발급 접수
              </button>
            </div>
          ))}
        </article>
      </section>
      {applicationResult ? (
        <p className="application-result-line">
          최근 고객센터 접수 {applicationResult.applicationReference} · {applicationResult.status}
        </p>
      ) : null}
    </section>
  );
}
