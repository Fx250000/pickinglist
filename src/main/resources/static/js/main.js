/**
 * MAIN.JS - Global Application Logic
 * Purpose: Handles Alerts, Printing, and simplified AJAX.
 */

document.addEventListener('DOMContentLoaded', () => {
    console.log("Office Green System: Online");
    initFlashMessages();
    setupGlobalEvents();
});

// --- 1. SIMPLIFIED FETCH (No Security Headers needed) ---
async function secureFetch(url, options = {}) {
    if (!options.headers) {
        options.headers = {};
    }
    // Default to JSON for data transfer
    if (!options.headers['Content-Type']) {
        options.headers['Content-Type'] = 'application/json';
    }
    return fetch(url, options);
}

// --- 2. UI UTILITIES ---

/* Auto-hide success messages after 5 seconds */
function initFlashMessages() {
    const alert = document.querySelector('.flash-message');
    if (alert) {
        setTimeout(() => {
            alert.style.transition = "opacity 0.5s ease";
            alert.style.opacity = "0";
            setTimeout(() => alert.remove(), 500);
        }, 5000);
    }
}

/* Handle specific button actions globally */
function setupGlobalEvents() {
    // Print Button
    const printBtn = document.querySelector('.btn-print');
    if (printBtn) {
        printBtn.addEventListener('click', () => window.print());
    }

    // Confirmation for Delete actions
    const deleteForms = document.querySelectorAll('form.confirm-delete');
    deleteForms.forEach(form => {
        form.addEventListener('submit', (e) => {
            if (!confirm('ATENÇÃO: Deseja realmente excluir esta OP do Backlog?')) {
                e.preventDefault();
            }
        });
    });
}