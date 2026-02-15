package com.tr1l.billing.application.command;

import java.time.LocalDate;

public record BillingWorkEnqueueCommand (LocalDate billingYearMonth,long userId){
}
