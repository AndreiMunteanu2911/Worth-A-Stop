const form = document.querySelector('.profile-follow');

if (form) {
    const button = form.querySelector('button');
    const label = button.textContent;

    form.addEventListener('submit', event => {
        if (button.disabled) {
            event.preventDefault();
            return;
        }
        button.disabled = true;
        button.textContent = 'Updating…';
    });

    window.addEventListener('pageshow', () => {
        button.disabled = false;
        button.textContent = label;
    });
}
