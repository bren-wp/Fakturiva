'use strict';
/* Native HTML forms provide the fallback. This enhances PIN keypad input only. */
for(const field of document.querySelectorAll('[data-numeric-pin]')){
  const normalize=()=>{field.value=field.value.replace(/[^0-9]/g,'').slice(0,6);};
  field.addEventListener('input',normalize);
  field.addEventListener('paste',()=>queueMicrotask(normalize));
}
for(const grid of document.querySelectorAll('[data-keypad]')){
  const pin=document.querySelector('[data-keypad-input]');
  if(!pin)continue;
  grid.addEventListener('click',event=>{
    const key=event.target.closest('button');
    if(!key||!grid.contains(key))return;
    if(key.hasAttribute('data-backspace')){
      pin.value=pin.value.slice(0,-1);
    }else if(key.hasAttribute('data-digit') && pin.value.length<6){
      pin.value+=key.getAttribute('data-digit');
    }
    pin.dispatchEvent(new Event('input',{bubbles:true}));
    pin.focus({preventScroll:true});
  });
}
for(const form of document.querySelectorAll('[data-auth-form]')){
  form.addEventListener('submit',event=>{
    for(const input of form.querySelectorAll('[data-numeric-pin]')){
      input.value=input.value.replace(/[^0-9]/g,'').slice(0,6);
    }
    if(!form.checkValidity()){
      event.preventDefault();form.reportValidity();return;
    }
    if(form.dataset.submitted==='yes'){event.preventDefault();return;}
    form.dataset.submitted='yes';
    const submit=form.querySelector('button[type=submit]');
    // Do not disable the submit button: it could remove the submitted
    // name/value pair in future forms. A duplicate click is prevented above.
    if(submit)submit.setAttribute('aria-busy','true');
  });
}
