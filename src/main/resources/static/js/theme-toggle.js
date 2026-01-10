// Theme Toggle Functionality
(function() {
    // Get saved theme or default to dark
    const savedTheme = localStorage.getItem('theme') || 'dark';

    // Apply theme on page load BEFORE DOM renders to prevent flashing
    document.documentElement.setAttribute('data-theme', savedTheme);

    // Wait for DOM to load
    document.addEventListener('DOMContentLoaded', function() {
        const toggleButton = document.getElementById('themeToggle');

        if (toggleButton) {
            toggleButton.addEventListener('click', function() {
                // Get current theme
                const currentTheme = document.documentElement.getAttribute('data-theme');
                const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

                // Add rotation animation
                toggleButton.classList.add('rotating');

                // Update theme
                document.documentElement.setAttribute('data-theme', newTheme);
                localStorage.setItem('theme', newTheme);

                // Remove animation class after animation completes
                setTimeout(() => {
                    toggleButton.classList.remove('rotating');
                }, 500);
            });
        }
    });
})();
