// Use the player's own skip control once it becomes available during an ad.
(() => {
    const key = "__hideUrlSkipObserver";
    if (window[key]) window[key].disconnect();
    delete window[key];
    if (!enabled || location.hostname !== "music.youtube.com") return;
    let clicked;
    const skip = () => {
        const button = document.querySelector(
            ".ad-showing .ytp-skip-ad-button, .ad-showing .ytp-ad-skip-button-modern, .ad-showing .ytp-ad-skip-button"
        );
        if (!button || button.disabled || !button.getClientRects().length ||
            getComputedStyle(button).visibility !== "visible") {
            clicked = null;
        } else if (button !== clicked) {
            clicked = button;
            button.click();
        }
    };
    const observer = new MutationObserver(skip);
    window[key] = observer;
    observer.observe(document.documentElement, {
        childList: true, subtree: true, attributes: true,
        attributeFilter: ["class", "style", "disabled", "hidden"]
    });
    skip();
})();
