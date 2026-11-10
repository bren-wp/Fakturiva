'use strict';
// Email/password forms work natively without JavaScript.
for(const form of document.querySelectorAll('[data-auth-form]')){
  form.addEventListener('submit',event=>{
    if(!form.checkValidity()){
      event.preventDefault();
      form.reportValidity();
      return;
    }
    if(form.dataset.submitted==='yes'){
      event.preventDefault();
      return;
    }
    form.dataset.submitted='yes';
    const submit=form.querySelector('button[type=submit]');
    if(submit)submit.setAttribute('aria-busy','true');
  });
}
