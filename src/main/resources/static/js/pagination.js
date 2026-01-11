/**
 * Pagination utility for dashboard upcoming events
 * Displays 10 items per page with next/previous navigation
 */

const ITEMS_PER_PAGE = 10;
let currentPage = 1;
let totalPages = 1;
let allEvents = [];

/**
 * Initialize pagination on page load
 */
function initializePagination() {
    const eventsList = document.getElementById('upcomingEventsList');
    if (!eventsList) return;

    // Get all event items from the list
    allEvents = Array.from(eventsList.querySelectorAll('.upcoming-event-item'));

    if (allEvents.length === 0) {
        // No events, hide pagination controls
        hidePaginationControls();
        return;
    }

    // Calculate total pages
    totalPages = Math.ceil(allEvents.length / ITEMS_PER_PAGE);
    currentPage = 1;

    // Show the first page
    updatePageDisplay();
    updatePaginationButtons();
}

/**
 * Display events for the current page
 */
function updatePageDisplay() {
    const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
    const endIndex = startIndex + ITEMS_PER_PAGE;

    // Hide all events
    allEvents.forEach(event => {
        event.style.display = 'none';
    });

    // Show only current page events
    allEvents.slice(startIndex, endIndex).forEach(event => {
        event.style.display = 'list-item';
    });

    // Update page info
    const pageInfo = document.getElementById('pageInfo');
    if (pageInfo) {
        pageInfo.textContent = `Page ${currentPage} of ${totalPages}`;
    }
}

/**
 * Update the state of pagination buttons
 */
function updatePaginationButtons() {
    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');

    if (prevBtn) {
        prevBtn.disabled = currentPage === 1;
    }
    if (nextBtn) {
        nextBtn.disabled = currentPage === totalPages;
    }
}

/**
 * Show the next page
 */
function showNextPage() {
    if (currentPage < totalPages) {
        currentPage++;
        updatePageDisplay();
        updatePaginationButtons();
        scrollToTop();
    }
}

/**
 * Show the previous page
 */
function showPreviousPage() {
    if (currentPage > 1) {
        currentPage--;
        updatePageDisplay();
        updatePaginationButtons();
        scrollToTop();
    }
}

/**
 * Scroll to the top of the events list
 */
function scrollToTop() {
    const eventsList = document.getElementById('upcomingEventsList');
    if (eventsList) {
        eventsList.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

/**
 * Hide pagination controls if there are no events
 */
function hidePaginationControls() {
    const paginationFooter = document.querySelector('.card-footer');
    if (paginationFooter) {
        paginationFooter.style.display = 'none';
    }
}

// Initialize pagination when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    initializePagination();
});
