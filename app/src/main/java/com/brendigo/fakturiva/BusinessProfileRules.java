package com.brendigo.fakturiva;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Baseline fields by country/legal form. These are data-entry guards,
 * not a certificate that a document complies with each jurisdiction.
 */
public final class BusinessProfileRules {
    public static final String[] COUNTRIES={"Hrvatska (HR)","Ujedinjeno Kraljevstvo (GB)",
        "Sjedinjene Države (US)","Njemačka (DE)","Druga država"};
    public static final String[] COUNTRY_CODES={"HR","GB","US","DE","OTHER"};
    public static final String[] FORMS={
        "Paušalni obrt (HR)","Obrt — dohodak (HR)","d.o.o. (HR)",
        "j.d.o.o. (HR)","Ltd (GB)","Inc (US)","LLC (US)",
        "GmbH (DE)","Drugi oblik"
    };
    public static final String[] FORM_CODES={
        "HR_PAUSAL","HR_OBRT","HR_DOO","HR_JDOO",
        "GB_LTD","US_INC","US_LLC","DE_GMBH","OTHER"
    };
    private BusinessProfileRules(){}
    public static String country(String code){
        if(code==null)return "OTHER";
        switch(code.trim().toUpperCase(Locale.ROOT)){
            case "HR":case "HRVATSKA":case "CROATIA":return "HR";
            case "GB":case "UK":case "UNITED KINGDOM":return "GB";
            case "US":case "USA":case "UNITED STATES":return "US";
            case "DE":case "GERMANY":case "DEUTSCHLAND":return "DE";
            default:return "OTHER";
        }
    }
    public static String form(String kind){
        if(kind==null)return "OTHER";
        String value=kind.trim().toUpperCase(Locale.ROOT);
        for(String form:FORM_CODES)if(form.equals(value))return form;
        if(value.equals("D.O.O."))return "HR_DOO";
        if(value.equals("J.D.O.O."))return "HR_JDOO";
        if(value.equals("PAUŠALNI OBRT"))return "HR_PAUSAL";
        if(value.equals("OBRT"))return "HR_OBRT";
        if(value.equals("LTD"))return "GB_LTD";
        if(value.equals("INC"))return "US_INC";
        if(value.equals("LLC"))return "US_LLC";
        if(value.equals("GMBH"))return "DE_GMBH";
        return "OTHER";
    }
    public static String compatibleCountry(String form){
        switch(form(form)){
            case "HR_PAUSAL":case "HR_OBRT":case "HR_DOO":case "HR_JDOO":return "HR";
            case "GB_LTD":return "GB";
            case "US_INC":case "US_LLC":return "US";
            case "DE_GMBH":return "DE";
            default:return "OTHER";
        }
    }
    public static boolean visible(String country,String form,String key){
        String c=country(country), f=form(form);
        if(key.equals("country")||key.equals("legal_form"))return false;
        if(key.equals("name")||key.equals("address")||key.equals("postal_code")||
           key.equals("city")||key.equals("email"))return true;
        switch(key){
            case "oib":return c.equals("HR");
            case "vat_id":return c.equals("HR")||c.equals("GB")||c.equals("DE");
            case "company_number":case "register_authority":case "registry":
                return f.equals("HR_DOO")||f.equals("HR_JDOO")||
                    f.equals("GB_LTD")||f.equals("US_INC")||
                    f.equals("US_LLC")||f.equals("DE_GMBH");
            case "bank_routing_number":return c.equals("US");
            case "bank_sort_code":return c.equals("GB");
            case "bank_account_number":return c.equals("US")||c.equals("GB");
            case "iban":case "swift":return !c.equals("US");
            case "business_unit":case "operator":return c.equals("HR");
            case "tax_note":case "tax_registration":case "invoice_reference":
                return true;
            default:return true;
        }
    }
    public static boolean required(String country,String form,String key,
                                   boolean fiscalizationEnabled){
        String c=country(country), f=form(form);
        if(key.equals("name")||key.equals("address")||key.equals("city")||
           key.equals("country")||key.equals("legal_form"))return true;
        if(c.equals("HR")&&key.equals("oib"))return true;
        if((f.equals("GB_LTD")||f.equals("US_INC")||f.equals("US_LLC")||
            f.equals("DE_GMBH")||f.equals("HR_DOO")||f.equals("HR_JDOO"))&&
            key.equals("company_number"))return true;
        if(c.equals("HR")&&fiscalizationEnabled&&
           (key.equals("business_unit")||key.equals("operator")))
            return true;
        return false;
    }
    public static void validate(String country,String form,Map<String,String> data,
                                boolean fiscalizationEnabled){
        String c=country(country),f=form(form);
        String nativeCountry=compatibleCountry(f);
        if(!"OTHER".equals(nativeCountry)&&!nativeCountry.equals(c))
            throw new IllegalArgumentException("Odabrani oblik tvrtke nije iz odabrane države.");
        for(String key:new String[]{"name","address","city","country","legal_form",
            "oib","company_number","business_unit","operator"}){
            if(required(c,f,key,fiscalizationEnabled)&&
               (data.get(key)==null||data.get(key).trim().isEmpty()))
                throw new IllegalArgumentException("Obvezan podatak: "+key);
        }
        if(c.equals("HR")&&!Oib.valid(data.get("oib")))
            throw new IllegalArgumentException("Hrvatski OIB mora imati 11 ispravnih znamenki.");
        String code=data.get("nkd_code");
        if(c.equals("HR")&&code!=null&&!code.isEmpty()&&
           !code.matches("[0-9]{2}(\\.[0-9]{1,2})?(\\.[0-9])?"))
            throw new IllegalArgumentException("Neispravan oblik NKD šifre.");
    }
}
