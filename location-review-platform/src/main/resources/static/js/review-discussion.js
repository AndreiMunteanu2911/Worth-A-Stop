document.querySelectorAll('form[data-review-submit]').forEach(form => {
    const button = form.querySelector('button[type="submit"]');
    const label = button.textContent;

    form.addEventListener('submit', event => {
        if (button.disabled) {
            event.preventDefault();
            return;
        }
        button.disabled = true;
        button.textContent = form.dataset.reviewSubmit;
    });

    window.addEventListener('pageshow', () => {
        button.disabled = false;
        button.textContent = label;
    });
});
