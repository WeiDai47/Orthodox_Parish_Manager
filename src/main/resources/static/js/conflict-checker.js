/**
 * Conflict checker for event scheduling
 * Uses AJAX to check for conflicts in real-time without page reload
 */

/**
 * Check for scheduling conflicts via AJAX
 * @param {number} parishionerId - The primary parishioner ID
 * @param {string} formType - 'sacrament' or 'regular' to identify which form
 */
function checkConflicts(parishionerId, formType = 'regular') {
    // Get elements based on form type
    const eventDateId = formType === 'sacrament' ? 'sacramentEventDate' : 'regularEventDate';
    const startTimeId = formType === 'sacrament' ? 'sacramentStartTime' : 'regularStartTime';
    const endTimeId = formType === 'sacrament' ? 'sacramentEndTime' : 'regularEndTime';
    const participantsId = formType === 'sacrament' ? 'sacramentAdditionalParticipants' : 'regularAdditionalParticipants';
    const warningId = formType === 'sacrament' ? 'sacramentConflictWarning' : 'regularEventConflictWarning';

    const eventDate = document.getElementById(eventDateId)?.value || '';
    const startTime = document.getElementById(startTimeId)?.value || '';
    const endTime = document.getElementById(endTimeId)?.value || '';
    const additionalParticipantsSelect = document.getElementById(participantsId);

    if (!eventDate) {
        hideConflictWarning(warningId);
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
        displayConflictWarning(data, warningId);
    })
    .catch(error => {
        console.error('Error checking conflicts:', error);
        hideConflictWarning(warningId);
    });
}

/**
 * Display conflict warning based on response data
 * @param {Object} data - Response data from conflict check endpoint
 * @param {string} warningId - ID of the warning div element
 */
function displayConflictWarning(data, warningId = 'regularEventConflictWarning') {
    const warningDiv = document.getElementById(warningId);
    if (!warningDiv) return;

    if (!data.hasConflicts) {
        hideConflictWarning(warningId);
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
 * @param {string} warningId - ID of the warning div element
 */
function hideConflictWarning(warningId = 'regularEventConflictWarning') {
    const warningDiv = document.getElementById(warningId);
    if (warningDiv) {
        warningDiv.innerHTML = '';
        warningDiv.style.display = 'none';
    }
}

/**
 * Set up event listeners for conflict checking on a specific form
 */
function setupConflictCheckingForForm(parishionerId, formType = 'regular') {
    const eventDateId = formType === 'sacrament' ? 'sacramentEventDate' : 'regularEventDate';
    const startTimeId = formType === 'sacrament' ? 'sacramentStartTime' : 'regularStartTime';
    const endTimeId = formType === 'sacrament' ? 'sacramentEndTime' : 'regularEndTime';
    const participantsId = formType === 'sacrament' ? 'sacramentAdditionalParticipants' : 'regularAdditionalParticipants';

    // Check conflicts when key fields change
    const eventDateSelect = document.getElementById(eventDateId);
    const startTimeSelect = document.getElementById(startTimeId);
    const endTimeSelect = document.getElementById(endTimeId);
    const participantsSelect = document.getElementById(participantsId);

    if (eventDateSelect) {
        eventDateSelect.addEventListener('change', () => checkConflicts(parishionerId, formType));
    }
    if (startTimeSelect) {
        startTimeSelect.addEventListener('change', () => checkConflicts(parishionerId, formType));
    }
    if (endTimeSelect) {
        endTimeSelect.addEventListener('change', () => checkConflicts(parishionerId, formType));
    }
    if (participantsSelect) {
        participantsSelect.addEventListener('change', () => checkConflicts(parishionerId, formType));
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

        // Set up conflict checking for both forms
        setupConflictCheckingForForm(parishionerId, 'sacrament');
        setupConflictCheckingForForm(parishionerId, 'regular');
    }
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    initializeConflictChecker();
});
