'use strict';
for(const button of document.querySelectorAll('[data-print-finance]')){
  button.addEventListener('click',()=>window.print());
}
