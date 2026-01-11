/**
 * Conflict checker for event scheduling
 * Uses AJAX to check for conflicts in real-time without page reload
 */

/**
 * Check for scheduling conflicts via AJAX
 * @param {number} parishionerId - The primary parishioner ID
 */
function checkConflicts(parishionerId) {
    const eventDate = document.getElementById('eventDate').value;
    const startTime = document.getElementById('startTime')?.value || '';
    const endTime = document.getElementById('endTime')?.value || '';
    const additionalParticipantsSelect = document.getElementById('additionalParticipants');

    if (!eventDate) {
        hideConflictWarning();
        return;
    }

    // Get selected additional participants
    let additionalParticipants = '';
    if (additionalParticipantsSelect) {
        const selectedOptions = Array.from(additionalParticipantsSelect.selectedOptions);
        additionalParticipants = selectedOptions.map(option => option.value).join(',');
    }

    // Build query parameters
    const params = new URLSearchParams();
    params.append('eventDate', eventDate);
    if (startTime) params.append('startTime', startTime);
    if (endTime) params.append('endTime', endTime);
    if (additionalParticipants) params.append('additionalParticipants', additionalParticipants);

    // Call AJAX endpoint
    fetch(`/parishioners/view/${parishionerId}/check-conflicts?${params.toString()}`, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        }
    })
    .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
    })
    .then(data => {
        displayConflictWarning(data);
    })
    .catch(error => {
        console.error('Error checking conflicts:', error);
        hideConflictWarning();
    });
}

/**
 * Display conflict warning based on response data
 * @param {Object} data - Response data from conflict check endpoint
 */
function displayConflictWarning(data) {
    const warningDiv = document.getElementById('conflictWarning');
    if (!warningDiv) return;

    if (!data.hasConflicts) {
        hideConflictWarning();
        return;
    }

    // Build warning message
    let warningHtml = `
        <div class="alert alert-warning alert-dismissible fade show" role="alert">
            <strong>⚠️ Scheduling Conflict Detected!</strong><br>
            Found ${data.conflictCount} conflict(s):
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    `;

    // Add database conflicts
    if (data.databaseConflicts && data.databaseConflicts.length > 0) {
        warningHtml += '<div class="mt-2"><strong>In Local Calendar:</strong><ul class="mb-0">';
        data.databaseConflicts.forEach(conflict => {
            warningHtml += `
                <li>
                    <strong>${conflict.parishionerName}</strong> - ${conflict.eventTitle}
                    on ${conflict.eventDate}
                    ${conflict.eventTime ? `at ${conflict.eventTime}` : '(all-day)'}
                </li>
            `;
        });
        warningHtml += '</ul></div>';
    }

    // Add Google Calendar conflicts
    if (data.googleCalendarConflicts && data.googleCalendarConflicts.length > 0) {
        warningHtml += '<div class="mt-2"><strong>In Google Calendar:</strong><ul class="mb-0">';
        data.googleCalendarConflicts.forEach(conflict => {
            warningHtml += `
                <li>
                    <strong>${conflict.eventTitle}</strong>
                    on ${conflict.eventDate}
                    ${conflict.eventTime ? `at ${conflict.eventTime}` : '(all-day)'}
                </li>
            `;
        });
        warningHtml += '</ul></div>';
    }

    warningHtml += '<div class="mt-2"><small>You can still create this event if needed.</small></div></div>';

    warningDiv.innerHTML = warningHtml;
    warningDiv.style.display = 'block';
}

/**
 * Hide the conflict warning
 */
function hideConflictWarning() {
    const warningDiv = document.getElementById('conflictWarning');
    if (warningDiv) {
        warningDiv.innerHTML = '';
        warningDiv.style.display = 'none';
    }
}

/**
 * Set up event listeners for conflict checking
 */
function setupConflictChecking(parishionerId) {
    // Check conflicts when key fields change
    const eventDateSelect = document.getElementById('eventDate');
    const startTimeSelect = document.getElementById('startTime');
    const endTimeSelect = document.getElementById('endTime');
    const participantsSelect = document.getElementById('additionalParticipants');

    if (eventDateSelect) {
        eventDateSelect.addEventListener('change', () => checkConflicts(parishionerId));
    }
    if (startTimeSelect) {
        startTimeSelect.addEventListener('change', () => checkConflicts(parishionerId));
    }
    if (endTimeSelect) {
        endTimeSelect.addEventListener('change', () => checkConflicts(parishionerId));
    }
    if (participantsSelect) {
        participantsSelect.addEventListener('change', () => checkConflicts(parishionerId));
    }
}

/**
 * Initialize conflict checker on page load
 */
function initializeConflictChecker() {
    // Get parishioner ID from page context (should be passed in data attribute)
    const pageElement = document.querySelector('[data-parishioner-id]');
    if (pageElement) {
        const parishionerId = pageElement.getAttribute('data-parishioner-id');
        setupConflictChecking(parishionerId);
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    initializeConflictChecker();
});
