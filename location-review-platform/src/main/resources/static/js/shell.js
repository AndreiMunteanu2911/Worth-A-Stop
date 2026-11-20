const toggle = document.querySelector('.menu-toggle');
const nav = document.querySelector('#site-nav');

if (toggle && nav) {
    const mobile = window.matchMedia('(max-width: 767px)');
    toggle.closest('.site-header').classList.add('has-menu');
    const close = () => {
        nav.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
    };
    toggle.addEventListener('click', () => {
        const open = toggle.getAttribute('aria-expanded') !== 'true';
        nav.classList.toggle('is-open', open);
        toggle.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && mobile.matches && toggle.getAttribute('aria-expanded') === 'true') {
            close();
            toggle.focus();
        }
    });
}
