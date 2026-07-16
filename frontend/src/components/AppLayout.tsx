import type { ReactNode } from 'react'
import type { PageKey, SystemStatus } from '../types'
import {
  BellIcon,
  CheckIcon,
  DownloadIcon,
  FileIcon,
  HeadphonesIcon,
  HelpIcon,
  TemplateIcon,
  UploadIcon,
} from '../icons'

type StepItem = {
  key: PageKey
  title: string
  description: string
  icon: ReactNode
}

const steps: StepItem[] = [
  { key: 'upload', title: 'Tải file BBNT', description: 'Nhập dữ liệu từ file Excel', icon: <UploadIcon size={21} /> },
  { key: 'work-items', title: 'Danh mục công việc', description: 'Danh sách nội dung công việc', icon: <FileIcon size={21} /> },
  { key: 'templates', title: 'Chọn biểu mẫu', description: 'Chọn mẫu biểu phù hợp', icon: <TemplateIcon size={21} /> },
  { key: 'preview', title: 'Xem trước & Xuất', description: 'Xem trước và xuất biểu mẫu', icon: <DownloadIcon size={21} /> },
]

export function AppLayout({
  page,
  onNavigate,
  canNavigate,
  children,
  status,
}: {
  page: PageKey
  onNavigate: (page: PageKey) => void
  canNavigate: (page: PageKey) => boolean
  children: ReactNode
  status: SystemStatus | null
}) {
  const activeIndex = steps.findIndex((step) => step.key === page)

  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img src="/hacom-logo-horizontal.png" alt="HaCom Holdings" />
        </div>

        <nav className="workflow-nav" aria-label="Các bước xử lý hồ sơ">
          {steps.map((step, index) => {
            const active = step.key === page
            const completed = index < activeIndex
            const enabled = canNavigate(step.key)
            return (
              <div className="workflow-step-wrap" key={step.key}>
                <button
                  type="button"
                  className={`workflow-step ${active ? 'active' : ''} ${completed ? 'completed' : ''}`}
                  disabled={!enabled}
                  onClick={() => enabled && onNavigate(step.key)}
                >
                  <span className="step-marker">
                    {completed ? <CheckIcon size={18} /> : <span>{index + 1}</span>}
                  </span>
                  <span className="step-icon">{step.icon}</span>
                  <span className="step-copy">
                    <strong>{step.title}</strong>
                    <small>{step.description}</small>
                  </span>
                </button>
                {index < steps.length - 1 && <span className={`step-line ${completed ? 'completed' : ''}`} />}
              </div>
            )
          })}
        </nav>

        <div className="sidebar-support">
          <span className="support-icon"><HeadphonesIcon size={25} /></span>
          <div>
            <strong>Hỗ trợ</strong>
            <p>Nếu cần hỗ trợ về file BBNT, vui lòng liên hệ đội dự án.</p>
          </div>
          <button type="button" onClick={() => window.alert('Vui lòng liên hệ quản trị viên dự án HaCom.')}>Liên hệ hỗ trợ</button>
        </div>
      </aside>

      <div className="content-column">
        <header className="topbar">
          <div className="product-title">
            <strong>HaCom BBNT Automation</strong>
            <span className="version-badge">V1.0</span>
          </div>
          <div className="topbar-actions">
            <button className="icon-button notification-button" type="button" aria-label="Thông báo">
              <BellIcon size={21} />
              <span className="notification-dot" />
            </button>
            <button className="icon-button" type="button" aria-label="Trợ giúp" title={status?.message}>
              <HelpIcon size={21} />
            </button>
            <div className="trial-user">
              <span className="trial-avatar">KT</span>
              <div>
                <strong>Khách hàng thử nghiệm</strong>
                <span>{status?.pdfAvailable ? `PDF sẵn sàng · ${status.activePdfEngine}` : 'PDF chưa sẵn sàng'}</span>
              </div>
            </div>
          </div>
        </header>

        <main className="main-content">{children}</main>
      </div>
    </div>
  )
}
