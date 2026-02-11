/**
 * PICKING-MODE.JS - Operational Logic
 * Purpose: Handles real-time quantity inputs, backlog filtering, and OP finalization.
 */

// STATE
let pickingState = [];

document.addEventListener('DOMContentLoaded', () => {
    initPickingTable();
    initEventListeners();
});

// --- INITIALIZATION ---
function initPickingTable() {
    // Scrape data from Thymeleaf table to build JS state
    const rows = document.querySelectorAll('tr.picking-row');

    pickingState = Array.from(rows).map(row => ({
        id: row.getAttribute('data-id'),
        name: row.cells[1].innerText, // Assumes Name is 2nd column
        requested: parseInt(row.querySelector('.qty-requested').innerText) || 0,
        found: 0,
        balance: parseInt(row.querySelector('.qty-requested').innerText) || 0
    }));

    console.log(`Loaded ${pickingState.length} items for picking.`);
}

function initEventListeners() {
    // 1. Inputs
    document.querySelectorAll('.input-qty').forEach(input => {
        input.addEventListener('input', (e) => {
            const rowId = e.target.closest('tr').getAttribute('data-id');
            handleQuantityChange(rowId, e.target.value);
        });
    });

    // 2. Report Button
    const btnReport = document.getElementById('btnGenerateReport');
    if (btnReport) {
        btnReport.addEventListener('click', generateBacklogReport);
    }

    // 3. Finalize Button
    const btnFinalize = document.getElementById('btnFinalizeOP');
    if (btnFinalize) {
        btnFinalize.addEventListener('click', finalizeOperation);
    }
}

// --- CORE LOGIC ---
function handleQuantityChange(id, value) {
    const item = pickingState.find(p => p.id === id);
    if (!item) return;

    const val = parseInt(value) || 0;
    item.found = val;
    item.balance = Math.max(0, item.requested - val);

    // UI Feedback
    const row = document.querySelector(`tr[data-id="${id}"]`);
    if (item.balance === 0) {
        row.classList.add('row-complete');
    } else {
        row.classList.remove('row-complete');
    }
}

function generateBacklogReport() {
    const backlog = pickingState.filter(i => i.balance > 0);

    if (backlog.length === 0) {
        alert("Lista completa! Nada pendente.");
        return;
    }

    // Simple Alert for now, or replace with the window.open logic we built previously
    let msg = "ITENS FALTANTES:\n";
    backlog.forEach(i => msg += `- ${i.name}: Falta ${i.balance}\n`);
    alert(msg);
}

async function finalizeOperation() {
    // 1. Prepare Data (Only send what is needed)
    const payload = pickingState.map(item => ({
        productId: item.id,
        quantityRequested: item.requested,
        quantityFound: item.found,
        quantityMissing: item.balance
    }));

    if (!confirm("Deseja finalizar esta OP e gravar as pendências?")) return;

    try {
        // Uses the secureFetch from main.js
        const response = await secureFetch('/api/picking/finalize', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (response.ok) {
            alert("OP Finalizada com Sucesso!");
            window.location.reload();
        } else {
            alert("Erro ao salvar.");
        }
    } catch (err) {
        console.error(err);
        alert("Erro de conexão.");
    }
}