/**
 * Time picker utility functions
 * Generates 30-minute interval options and handles auto-fill for end times
 */

/**
 * Generate time options in 30-minute intervals (00:00 to 23:30)
 * @returns {Array} Array of {value, label} objects for time selection
 */
function generateTimeOptions() {
    const options = [];
    options.push({value: '', label: '-- Not set (all-day) --'});

    for (let hour = 0; hour < 24; hour++) {
        for (let minute = 0; minute < 60; minute += 30) {
            const timeString = String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
            const label = formatTime12Hour(timeString);
            options.push({value: timeString, label: label});
        }
    }

    return options;
}

/**
 * Convert 24-hour time to 12-hour format with AM/PM
 * @param {string} time24 - Time in HH:mm format
 * @returns {string} Time in 12-hour format (e.g., "2:30 PM")
 */
function formatTime12Hour(time24) {
    const [hour, minute] = time24.split(':').map(Number);
    const ampm = hour >= 12 ? 'PM' : 'AM';
    const hour12 = hour % 12 || 12;
    return `${hour12}:${String(minute).padStart(2, '0')} ${ampm}`;
}

/**
 * Set up time picker event listeners
 * Auto-fills end time with 30 minutes after start time
 */
function setupTimeAutoFill() {
    const startTimeSelect = document.getElementById('startTime');
    const endTimeSelect = document.getElementById('endTime');

    if (startTimeSelect) {
        startTimeSelect.addEventListener('change', function() {
            if (this.value) {
                // Auto-fill end time as 30 minutes after start time
                const [hour, minute] = this.value.split(':').map(Number);
                let newHour = hour;
                let newMinute = minute + 30;

                if (newMinute >= 60) {
                    newMinute = 0;
                    newHour++;
                    if (newHour >= 24) {
                        newHour = 0; // Wrap around to next day
                    }
                }

                const endTimeValue = String(newHour).padStart(2, '0') + ':' + String(newMinute).padStart(2, '0');
                if (endTimeSelect) {
                    endTimeSelect.value = endTimeValue;
                }
            }
        });
    }
}

/**
 * Populate select elements with time options
 * @param {string} selectId - ID of the select element to populate
 */
function populateTimeSelect(selectId) {
    const select = document.getElementById(selectId);
    if (!select) return;

    const options = generateTimeOptions();
    select.innerHTML = '';

    options.forEach(option => {
        const optElement = document.createElement('option');
        optElement.value = option.value;
        optElement.textContent = option.label;
        select.appendChild(optElement);
    });
}

/**
 * Initialize time pickers on page load
 */
function initializeTimePickers() {
    // Populate start and end time selects
    populateTimeSelect('startTime');
    populateTimeSelect('endTime');

    // Setup auto-fill logic
    setupTimeAutoFill();
}

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function() {
    initializeTimePickers();
});
