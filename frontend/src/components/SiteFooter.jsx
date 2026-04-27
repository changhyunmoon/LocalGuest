import './SiteFooter.css'

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <div className="site-footer__left">
          <div className="site-footer__brand">LocalGuest</div>
          <p className="site-footer__copy">© 2026 LocalGuest. All rights reserved.</p>
        </div>
        <div className="site-footer__right">
          <p className="site-footer__tel">고객센터 1588-0000</p>
          <nav className="site-footer__links" aria-label="법적 고지">
            <a href="#">이용약관</a>
            <span className="site-footer__dot">·</span>
            <a href="#">개인정보처리방침</a>
          </nav>
        </div>
      </div>
    </footer>
  )
}
