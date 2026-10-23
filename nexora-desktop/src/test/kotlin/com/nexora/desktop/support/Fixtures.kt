package com.nexora.desktop.support

/**
 * API payloads copied from the examples and field lists of docs/05-API-SPEC.md. Totals are deliberately NOT always
 * the sum of their parts: the client must show the server's figure, never one it computed itself.
 */
object Fixtures {

    /** E-RPT-01. `totalAvailable` 330000.00 is not cash + listed banks (280000.00) on purpose. */
    const val DASHBOARD = """{
      "asOf":"2026-10-08T05:12:00Z","cash":80000.00,
      "banks":[{"account":{"id":"b7e1","accountName":"SBI Current","accountType":"BANK"},"balance":200000.00}],
      "totalAvailable":330000.00,"customerReceivables":500000.00,"customerAdvances":5000.00,
      "supplierPayables":310000.00,"supplierOverdue":45000.00,"manufacturerPayable":8000.00,"workerPayable":3200.00,
      "loanOutstandingPrincipal":350000.00,"chitMonthlyCommitments":25000.00,
      "stock":{"rawSilkKg":20.000,"internalWipKg":100.000,"externalWipKg":45.000,"finishedSilkWarpKg":12.500,"finishedKoraWarpKg":55.500,"vudaWarpKg":0.000},
      "ordersByStatus":{"PLACED":1,"CONFIRMED":2,"IN_PROGRESS":1,"READY":0},"lateOrders":1,
      "productionBatches":{"PLANNED":1,"IN_PROGRESS":1},"openOutsourcingJobs":2,
      "upcomingSupplierDues":[{"purchaseId":"8a1b","purchaseNumber":"PUR-2026-0003","supplier":{"id":"1f2e","partyCode":"PTY-2026-0003","name":"Ramesh Silks"},"dueDate":"2026-10-05","outstanding":45000.00,"overdue":true,"overdueDays":3}],
      "recentTransactions":[{"id":"t1","account":{"id":"b7e1","accountName":"SBI Current","accountType":"BANK"},"transactionDate":"2026-10-07","transactionType":"CUSTOMER_PAYMENT","direction":"IN","amount":45000.00,"referenceType":"PAYMENT","referenceNumber":"PAY-2026-0001"}]
    }"""

    fun party(id: String = "5d0c", roles: String = "\"CUSTOMER\"", status: String = "ACTIVE") = """{
      "id":"$id","partyCode":"PTY-2026-0001","name":"Kumar Silks","roles":[$roles],"status":"$status",
      "city":"Kanchipuram","state":"Tamil Nadu","phones":[{"id":"a1","phoneNumber":"+91 98400 12345","label":"mobile","primary":true}],
      "defaultCreditDays":null,"defaultRollingRatePerHour":null,"defaultWarpingRatePerKg":null,
      "createdAt":"2026-10-07T10:00:00Z","updatedAt":"2026-10-07T10:00:00Z","createdBy":"7c1e"
    }"""

    /** E-PROC-03 PurchaseResponse; defaults are the 05 example after an 80 kg receipt. */
    fun purchase(
        status: String = "PARTIALLY_RECEIVED",
        received: String = "80.000",
        accepted: String = "80.000",
        returned: String = "0.000",
        remaining: String = "170.000",
        allocated: String = "0.00",
        outstanding: String = "64000.00",
    ) = """{
      "id":"8a1b","purchaseNumber":"PUR-2026-0001","supplier":{"id":"1f2e","partyCode":"PTY-2026-0003","name":"Ramesh Silks"},
      "productType":"RAW_SILK","purchaseDate":"2026-10-01","orderedWeightKg":250.000,"ratePerKg":800.00,"orderedValue":200000.00,
      "paymentType":"CREDIT","creditDays":10,"dueDate":"2026-10-11","billReference":null,"status":"$status",
      "receivedWeightKg":$received,"acceptedWeightKg":$accepted,"rejectedWeightKg":0.000,"returnedWeightKg":$returned,
      "remainingWeightKg":$remaining,"payableAmount":64000.00,"allocatedAmount":$allocated,"outstandingAmount":$outstanding,
      "paymentStatus":"UNPAID","overdue":false,"overdueDays":0,
      "receipts":[{"id":"r1","receiptNumber":"RCT-2026-0001","receiptDate":"2026-10-03","receivedWeightKg":80.000,"acceptedWeightKg":80.000,"rejectedWeightKg":0.000,"movementNumber":"MOV-2026-0001"}],
      "returns":[],"notes":null
    }"""

    /** E-ORD-03 OrderResponse (05 example: one kora warp item, 100 kg at 850.00). */
    fun order(status: String = "PLACED", locked: Boolean = false, modifiable: Boolean = true) = """{
      "id":"4c2d","orderNumber":"ORD-2026-0001","customer":{"id":"5d0c","partyCode":"PTY-2026-0001","name":"Kumar Silks"},
      "orderDate":"2026-10-07","requiredDate":"2026-10-20","deliveredOn":null,"status":"$status","locked":$locked,"modifiable":$modifiable,
      "late":false,"items":[{"id":"e1f2","lineNo":1,"productType":"KORA_WARP","requiredWeightKg":100.000,"ratePerKg":850.00,
      "amount":85000.00,"warpSpecification":null,"status":"PENDING","allocatedAmount":0.00,"outstandingAmount":0.00,"paymentStatus":"UNPAID"}],
      "totalAmount":85000.00,"allocatedAmount":0.00,"outstandingAmount":0.00,"paymentStatus":"UNPAID",
      "productionBatches":[],"outsourcingJobs":[],"notes":null
    }"""

    /** E-PAY-01 response example: 45000.00 allocated 20000 + 25000 oldest first. */
    const val PAYMENT = """{
      "id":"c1d2","paymentNumber":"PAY-2026-0001","purpose":"CUSTOMER","direction":"RECEIVED",
      "party":{"id":"5d0c","partyCode":"PTY-2026-0001","name":"Kumar Silks"},"amount":45000.00,"allocatedAmount":45000.00,
      "unallocatedAmount":0.00,"paymentMethod":"UPI","account":{"id":"b7e1","accountName":"SBI Current","accountType":"BANK"},
      "paymentDate":"2026-10-08","referenceNumber":null,"status":"RECORDED","reversedAt":null,"reversalReason":null,
      "allocations":[
        {"id":"a1","targetType":"ORDER_ITEM","targetId":"i1","targetLabel":"ORD-2026-0001 line 1","obligationDate":"2026-10-01","amount":20000.00,"released":false},
        {"id":"a2","targetType":"ORDER_ITEM","targetId":"i2","targetLabel":"ORD-2026-0002 line 1","obligationDate":"2026-10-03","amount":25000.00,"released":false}
      ],"notes":null
    }"""

    /** E-PAY-06 with one open order whose single item still owes 20000.00. */
    const val CUSTOMER_OUTSTANDING = """{"customer":{"id":"5d0c","name":"Kumar Silks"},"totalOutstanding":20000.00,"advance":0.00,
      "ageing":{"days0to30":20000.00,"days31to60":0.00,"days61to90":0.00,"over90":0.00},
      "orders":[{"orderId":"4c2d","orderNumber":"ORD-2026-0001","orderDate":"2026-10-01","status":"CONFIRMED","amount":85000.00,"allocated":65000.00,"outstanding":20000.00,"ageDays":7}],
      "openingObligations":[]}"""

    fun page(content: String, total: Int = 1) = """{"content":[$content],"page":0,"size":20,"totalElements":$total,"totalPages":1}"""

    const val ACCOUNTS = """{"accounts":[
      {"id":"c1","accountName":"Cash","accountType":"CASH","bankName":null,"maskedAccountNumber":null,"status":"ACTIVE","balance":80000.00},
      {"id":"c2","accountName":"Shop cash","accountType":"CASH","bankName":null,"maskedAccountNumber":null,"status":"ACTIVE","balance":0.00},
      {"id":"b7e1","accountName":"SBI Current","accountType":"BANK","bankName":"State Bank of India","maskedAccountNumber":"XXXXXX4821","status":"ACTIVE","balance":200000.00},
      {"id":"b9","accountName":"Old Canara","accountType":"BANK","bankName":"Canara Bank","maskedAccountNumber":"XXXXXX1111","status":"CLOSED","balance":0.00}
    ],"totalAvailable":280000.00}"""
}
