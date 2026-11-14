"use strict";
document.addEventListener("DOMContentLoaded", () => {
  const template = document.getElementById("document-line-template");
  if (!(template instanceof HTMLTemplateElement)) return;
  document.querySelectorAll("[data-add-document-line]").forEach(button => {
    button.addEventListener("click", () => {
      const form = button.closest("form");
      const list = form?.querySelector("[data-document-lines]");
      if (!(list instanceof HTMLElement)) return;
      const count = list.querySelectorAll(".document-line").length;
      if (count >= 40) {
        button.disabled = true;
        return;
      }
      const node = template.content.cloneNode(true);
      const fieldset = node.querySelector("fieldset");
      if (!fieldset) return;
      const legend = fieldset.querySelector("legend");
      if (legend) legend.textContent = "Stavka " + (count + 1);
      fieldset.querySelectorAll("[name]").forEach(input => {
        const raw = input.getAttribute("name");
        if (raw) input.setAttribute("name", raw.replace("__INDEX__", String(count)));
      });
      list.appendChild(node);
      fieldset.querySelector("input")?.focus();
      if (count + 1 >= 40) button.disabled = true;
    });
  });
  // Browser print / Save as PDF uses only the document's current decrypted
  // read-only preview; never sends its plaintext to another endpoint.
  document.querySelectorAll("[data-print-document]").forEach(button => {
    button.addEventListener("click", () => {
      const card = button.closest("[data-print-card]");
      if (!(card instanceof HTMLElement)) return;
      document.querySelectorAll(".print-target").forEach(element => {
        element.classList.remove("print-target");
      });
      card.classList.add("print-target");
      document.body.classList.add("print-document");
      window.print();
    });
  });
  window.addEventListener("afterprint", () => {
    document.body.classList.remove("print-document");
    document.querySelectorAll(".print-target").forEach(element => {
      element.classList.remove("print-target");
    });
  });
  document.querySelectorAll("[data-confirm-delete]").forEach(button => {
    button.addEventListener("click", event => {
      if (!window.confirm("Trajno izbrisati ovaj NACRT? Potvrđeni dokumenti nisu obrisivi.")) {
        event.preventDefault();
      }
    });
  });
});