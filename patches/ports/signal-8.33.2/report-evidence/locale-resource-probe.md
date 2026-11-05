# Four localized resource preservation checks

The four passing single-file compile checks reverse additions of `please_wait` under `app/src/main/res/values-*/strings.xml`. The whole input blobs and report hunk IDs match source PR3 `129bd88f3f04dfb6b80a93b96ec026f6a5cd3010` and PR4 `2b0ba46b23fd27974b8cefbd36e0d91791b525bd`. Each PR4 value matches old Molly tree `0e202dda90785771ff8c87526eaaf30c655f54f3` exactly at the XML element level:

| Hunk | Locale | Old Molly and PR4 value | Reversal contract |
| --- | --- | --- | --- |
| 8080b27cf35752b109de | af | Wag asseblief… | fails: af resource missing |
| 1b0c2e283e4bcbf45694 | ar | يُرجى الانتظار… | fails: ar resource missing |
| 0ef6b96ddb2b6a12d036 | az | Zəhmət olmasa gözləyin… | fails: az resource missing |
| 4f9c3af9c3c25445405c | be | Пачакайце, калі ласка… | fails: be resource missing |

The old Molly default is `Please wait…` in `app/src/main/res/values/strings.xml`; PR4 retains that value in `app/src/main/res/values/strings2.xml`. Signal tree `6e9dddc01f1b26e608ac683807c51e139c141ed2` has no `please_wait` definition in these app locale or default directories. The translation task operates on existing localized entries selected by `mollyify=true` in base strings.xml and substitutes branding; it does not create the removed entries.

The scratch fixture extracted these exact current XML elements, compared each with old Molly, compiled them with cached AAPT2 2.20-15978811, then linked and dumped one baseline and four cases each missing one locale. The baseline resource table contains the default and all four old Molly locale values; each counterfactual links successfully but lacks its corresponding locale configuration while retaining the default and other locales. Thus each counterfactual fails the specific contract of retaining that old Molly locale entry; this is not a linker failure. Commands and assertions are in the companion script, complete resource tables and compiler/SDK hashes in the log. This is a minimal resource-table probe, not a full app link or device rendering test.

The first setup attempt stopped before compilation because the XML library included surrounding indentation in its element serialization comparison. The second excluded the element tail (outside the element) and completed. The probe's private XML, flat files and APKs were removed; only text evidence remains.
