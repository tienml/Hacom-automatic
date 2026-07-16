import type { ReactNode } from 'react'
import type { PageKey, SystemStatus } from '../types'
import {
  BellIcon,
  ClockIcon,
  DownloadIcon,
  FileIcon,
  HelpIcon,
  SettingsIcon,
  TemplateIcon,
  UploadIcon,
} from '../icons'

type NavItem = {
  key: PageKey | 'history' | 'settings'
  label: string
  icon: ReactNode
}

const primaryNav: NavItem[] = [
  { key: 'upload', label: 'Tải file BBNT', icon: <UploadIcon /> },
  { key: 'work-items', label: 'Danh mục công việc', icon: <FileIcon /> },
  { key: 'templates', label: 'Chọn biểu mẫu', icon: <TemplateIcon /> },
  { key: 'preview', label: 'Xem trước & Xuất', icon: <DownloadIcon /> },
]

const secondaryNav: NavItem[] = [
  { key: 'history', label: 'Lịch sử xử lý', icon: <ClockIcon /> },
  { key: 'settings', label: 'Cài đặt', icon: <SettingsIcon /> },
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
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <img src="/hacom-logo-horizontal.png" alt="HaCom Holdings" />
        </div>

        <nav className="sidebar-nav" aria-label="Điều hướng chính">
          {primaryNav.map((item) => {
            const enabled = canNavigate(item.key as PageKey)
            return (
              <button
                type="button"
                key={item.key}
                className={`nav-item ${page === item.key ? 'active' : ''}`}
                disabled={!enabled}
                onClick={() => enabled && onNavigate(item.key as PageKey)}
              >
                <span className="nav-icon">{item.icon}</span>
                <span>{item.label}</span>
              </button>
            )
          })}

          <div className="nav-divider" />

          {secondaryNav.map((item) => (
            <button type="button" key={item.key} className="nav-item nav-disabled" disabled>
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
              <span className="v2-badge">V2</span>
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <img src="/hacom-logo-vertical.png" alt="HaCom Holdings" />
          <div className="footer-divider" />
          <p>Phiên bản 1.0.0</p>
          <small>Không đăng nhập · Không database</small>
        </div>
      </aside>

      <div className="content-column">
        <header className="topbar">
          <div className="product-title">
            <span className="product-icon"><FileIcon size={20} /></span>
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
                <span>{status?.pdfAvailable ? `PDF: ${status.activePdfEngine}` : 'PDF chưa sẵn sàng'}</span>
              </div>
            </div>
          </div>
        </header>

        <main className="main-content">{children}</main>
      </div>
    </div>
  )
}
