/**
 * Gmail Composition JavaScript
 * Handles recipient selection, autocomplete search, and form management
 */

// Global state
let allRecipients = [];
let selectedRecipients = new Set();
let selectedGroups = new Set();

// Load recipients on page load
document.addEventListener('DOMContentLoaded', function() {
    fetchRecipients().then(function() {
        setupSearchListener();
        setupGroupCheckboxes();
        setupFormSubmit();
        handlePreselectedRecipients();
    });
});

/**
 * Fetch all non-departed parishioners from server
 */
function fetchRecipients() {
    return fetch('/gmail/recipients')
        .then(response => response.json())
        .then(data => {
            allRecipients = data;
            console.log('Loaded ' + allRecipients.length + ' recipients');
        })
        .catch(error => console.error('Error loading recipients:', error));
}

/**
 * Setup search input listener for autocomplete
 */
function setupSearchListener() {
    const searchInput = document.getElementById('parishionerSearch');
    const autocompleteResults = document.getElementById('autocompleteResults');
    const autocompleteList = document.getElementById('autocompleteList');

    searchInput.addEventListener('input', function() {
        const query = this.value.toLowerCase().trim();

        if (query.length < 2) {
            autocompleteResults.style.display = 'none';
            return;
        }

        // Filter recipients
        const matches = allRecipients.filter(r =>
            r.fullName.toLowerCase().includes(query) ||
            (r.email && r.email.toLowerCase().includes(query)) ||
            (r.householdName && r.householdName.toLowerCase().includes(query))
        ).slice(0, 10); // Limit to 10 results

        if (matches.length === 0) {
            autocompleteResults.style.display = 'none';
            return;
        }

        // Build autocomplete list
        autocompleteList.innerHTML = matches.map(r => `
            <div class="autocomplete-item" onclick="addRecipient(${r.parishionerId})">
                <strong>${r.fullName}</strong>
                <br>
                <small class="text-muted">
                    ${r.email ? r.email : 'No email'} | ${r.status} | ${r.householdName ? r.householdName : 'No household'}
                </small>
            </div>
        `).join('');

        autocompleteResults.style.display = 'block';
    });

    // Close autocomplete when clicking outside
    document.addEventListener('click', function(e) {
        if (!searchInput.contains(e.target) && !autocompleteResults.contains(e.target)) {
            autocompleteResults.style.display = 'none';
        }
    });
}

/**
 * Add recipient to selection
 */
function addRecipient(parishionerId) {
    const recipient = allRecipients.find(r => r.parishionerId === parishionerId);
    if (!recipient) return;

    // Check if already added
    if (selectedRecipients.has(parishionerId)) {
        alert(recipient.fullName + ' is already in the recipient list');
        return;
    }

    selectedRecipients.add(parishionerId);
    updateRecipientDisplay();

    // Clear search
    document.getElementById('parishionerSearch').value = '';
    document.getElementById('autocompleteResults').style.display = 'none';
}

/**
 * Remove recipient from selection
 */
function removeRecipient(parishionerId) {
    selectedRecipients.delete(parishionerId);
    updateRecipientDisplay();
}

/**
 * Update visual display of selected recipients
 */
function updateRecipientDisplay() {
    const recipientList = document.getElementById('recipientList');

    if (selectedRecipients.size === 0) {
        recipientList.innerHTML = '<small class="text-muted">Selected recipients will appear here...</small>';
        return;
    }

    const badges = Array.from(selectedRecipients).map(id => {
        const recipient = allRecipients.find(r => r.parishionerId === id);
        if (!recipient) return '';

        const hasEmail = recipient.email && recipient.email.trim() !== '';
        const badgeClass = hasEmail ? 'bg-success' : 'bg-warning text-dark';

        return `
            <span class="badge ${badgeClass} recipient-badge"
                  onclick="removeRecipient(${id})"
                  title="${hasEmail ? recipient.email : 'No email on file'}">
                ${recipient.fullName} ${!hasEmail ? '(no email)' : ''}
                <i class="bi bi-x-circle ms-1"></i>
            </span>
        `;
    }).join('');

    recipientList.innerHTML = badges;
}

/**
 * Setup group status checkboxes to add/remove parishioners by status
 */
function setupGroupCheckboxes() {
    const groupCheckboxes = document.querySelectorAll('input[name="groupStatuses"]');

    groupCheckboxes.forEach(checkbox => {
        checkbox.addEventListener('change', function() {
            const status = this.value;

            if (this.checked) {
                // Add all parishioners with this status
                selectedGroups.add(status);
                const parishionersWithStatus = allRecipients.filter(r => r.status === status);
                parishionersWithStatus.forEach(p => {
                    selectedRecipients.add(p.parishionerId);
                });
            } else {
                // Remove all parishioners with this status
                selectedGroups.delete(status);
                const parishionersWithStatus = allRecipients.filter(r => r.status === status);
                parishionersWithStatus.forEach(p => {
                    selectedRecipients.delete(p.parishionerId);
                });
            }

            updateRecipientDisplay();
        });
    });
}

/**
 * Handle preselected recipients from URL query parameter (from view-parishioner.html)
 */
function handlePreselectedRecipients() {
    const urlParams = new URLSearchParams(window.location.search);
    const preselectedIds = urlParams.get('preselectedIds');

    if (!preselectedIds) {
        return; // No preselected IDs, nothing to do
    }

    // Parse comma-separated IDs
    const ids = preselectedIds.split(',').map(id => {
        const trimmed = id.trim();
        return parseInt(trimmed);
    }).filter(id => !isNaN(id)); // Filter out any invalid numbers

    // Add each ID to selectedRecipients if it exists in allRecipients
    ids.forEach(id => {
        if (allRecipients.some(r => r.parishionerId === id)) {
            selectedRecipients.add(id);
        }
    });

    // Update display to show all preselected recipients as badges
    if (ids.length > 0) {
        updateRecipientDisplay();
        console.log('Preselected ' + selectedRecipients.size + ' recipients from view-parishioner');
    }
}

/**
 * Setup form submit to include selected recipient IDs
 */
function setupFormSubmit() {
    const form = document.getElementById('emailForm');
    form.addEventListener('submit', function(e) {
        // Check if any recipients are selected
        if (selectedRecipients.size === 0) {
            e.preventDefault();
            alert('Please select at least one recipient (individual or by group status)');
            return;
        }

        const idsInput = document.getElementById('individualRecipientsInput');
        idsInput.value = Array.from(selectedRecipients).join(',');
    });
}
