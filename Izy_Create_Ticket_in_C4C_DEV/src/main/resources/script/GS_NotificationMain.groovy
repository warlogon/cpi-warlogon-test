import com.sap.gateway.ip.core.customdev.util.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Map
import groovy.json.JsonSlurper
import groovy.json.JsonOutput


def validateInboundJson(Message message) {
    
    def errors = []
    def body = message.getBody(String)
    
    try {
        
        def json = new JsonSlurper().parseText(body)
        
        // Set customer details
        message.setProperty("mp_LegacyCustomerID", json.customerNumberLegacy)
        
        
		//SE2-626
		def salesOrg = json.salesOrg ?: 'FR11'
		def C4C_User = ''
		def validServiceCategories = ["LENSES", "ACCBILL"]
		def validIncidentCategories = ["AB_BILLINGCLAIM", "L_ORDER", "L_COMMREQ", "L_FOLLOW", "L_ISSUE"]
		def validObjectCategories = ["L_O_TAILORED", "L_O_STUDY/QUOT", "L_O_EQUIP", "L_C_TESTLENSES","L_F_ORDERCANCELLATION","L_C_PRODUIT"]
		def validTeams = [
					"BBGRFRAGENCELYON",
					"BBGRFRAGENCERENNES",
					"BBGRFRAGENCELILLE",
					"BBGRFRAGENCEPROVINS",
					"BBGRFRAGENCETOULOUSE",
					"BBGRFRBACKOFFICE",
					"ESSFRPOLESUPPORT",
					"ESSFRBACKOFFICE",
					"ESFRTECHNIQUE",
					"ESS_FR_HGCALCUL",
					"ESS_FR_BATTANTS",
					"ESS_FR_BORBA"
				]
		
		switch (salesOrg) {
			case 'FR11':
				C4C_User = message.getProperty("CONST_C4C_User_FR11") //see4c_IZYCPITECH_qa
				
				// Allowed values
				//validServiceCategories = ["LENSES", "ACCBILL"]
				//validIncidentCategories = ["AB_BILLINGCLAIM", "L_ORDER", "L_COMMREQ"]
				//validObjectCategories = ["L_O_TAILORED", "L_O_STUDY/QUOT", "L_O_EQUIP", "L_C_TESTLENSES"]
				//validTeams = [
				//	"ESSFRPOLESUPPORT", "ESSFRBACKOFFICE", "ESFRTECHNIQUE",
				//	"ESS_FR_HGCALCUL", "ESS_FR_BATTANTS", "ESS_FR_BORBA"
				//]
				break
			case 'FR31':
				C4C_User = message.getProperty("CONST_C4C_User_FR31") //see4c_IZYCPITECH_BBGRFR_qa
				//validServiceCategories = ["LENSES"]
				//validIncidentCategories = ["L_FOLLOW","L_ISSUE"]
				//validObjectCategories = ["L_F_ORDERCANCELLATION","L_C_PRODUIT"]
				//validTeams = [
				//	"BBGRFRAGENCELYON",
				//	"BBGRFRAGENCERENNES",
				//	"BBGRFRAGENCELILLE",
				//	"BBGRFRAGENCEPROVINS",
				//	"BBGRFRAGENCETOULOUSE",
				//	"BBGRFRBACKOFFICE"
				//]
				break
			default:
				errors << "Invalid SalesOrg field: ${salesOrg}. Should be FR11 or FR31"
		}

		if (!C4C_User) {
			errors << "C4C_User is empty for SalesOrg: ${salesOrg}"
		}
		message.setProperty("mp_C4C_User", C4C_User)
		message.setProperty("mp_CustomerSalesOrgID", salesOrg)
		// / SE2-626
		
        // Mandatory fields
        def mandatoryFields = ["customerNumberLegacy", "subject", "description", "team", "status"]


        def nullAllowed = ["description", "objectCategory", "team"]
        mandatoryFields.each { field ->
            if (!json[field] && !nullAllowed.contains(field)) {
                errors << "Missing required field: ${field}"
            }
        }
    
        // Allowed values
        def validStatus = ["1", "5"]
        def validMedicalOptician = ["101", "111"] //101=Medical, 111=Optician
        def validtestSampleLenses = ["101", "111", "121", "131", "141", "151", "161", "171"]
        // Codes represent Test Sample Lenses:
        // 101 - Test Lenses
        // 111 - Sample Lenses
        // 121 - Campaign Test Lenses
        // 131 - Professional uses - Lenses
        // 141 - Professional uses - Mount
        // 151 - Professional uses - Lenses + Client frame
        // 161 - Professional uses - Lenses + Mounting
        // 171 - Professional Uses - Beveled Lenses
        
        if (json.serviceCategory && !validServiceCategories.contains(json.serviceCategory)) {
            errors << "Invalid serviceCategory: ${json.serviceCategory}. Must be one of ${validServiceCategories}"
        }
        if (json.incidentCategory && !validIncidentCategories.contains(json.incidentCategory)) {
            errors << "Invalid incidentCategory: ${json.incidentCategory}. Must be one of ${validIncidentCategories}"
        }
        if (json.objectCategory && !validObjectCategories.contains(json.objectCategory)) {
            errors << "Invalid objectCategory: ${json.objectCategory}. Must be one of ${validObjectCategories}"
        }
        
        // Validate approvalRequired is boolean if present
        if (json.containsKey('approvalRequired')) {
            def value = json.approvalRequired
            if (!(value instanceof Boolean)) {
                errors << "Invalid approvalRequired: '${value}'. Must be a boolean (true/false)."
            }
        }

        if (json.istherequestforaMedicalProfessionalorforanOptician && !validMedicalOptician.contains(json.istherequestforaMedicalProfessionalorforanOptician)) {
            errors << "Invalid istherequestforaMedicalProfessionalorforanOptician: ${json.istherequestforaMedicalProfessionalorforanOptician}. Must be one of ${validMedicalOptician}"
        }
        if (json.testSampleLenses && !validtestSampleLenses.contains(json.testSampleLenses)) {
            errors << "Invalid testSampleLenses: ${json.testSampleLenses}. Must be one of ${validtestSampleLenses}"
        }
        // Validate testSampleLensesQuantity is numeric
        if (json.testSampleLensesQuantity != null) {
            def quantityStr = json.testSampleLensesQuantity.toString().trim()
            try {
                // Check if it's numeric
                new BigDecimal(quantityStr)  // Just to validate numeric content
                // Always keep as string for C4C KUT fields
                message.setProperty("mp_testSampleLensesQuantity", quantityStr)
            } catch (Exception e) {
                errors << "Invalid testSampleLensesQuantity: '${json.testSampleLensesQuantity}'. Must be numeric."
            }
        }

        if (json.team && !validTeams.contains(json.team)) {
            errors << "Invalid team: ${json.team}. Must be one of ${validTeams}"
        }
        if (json.status && !validStatus.contains(json.status)) {
            errors << "Invalid status: ${json.status}. Must be one of ${validStatus}"
        }
        if (json.attachments) {
            json.attachments.eachWithIndex { att, idx ->
                if (!att.fileName) errors << "Attachment #${idx+1} missing fileName"
                if (!att.mimeType) errors << "Attachment #${idx+1} missing mimeType"
                if (!att.content) {
                    errors << "Attachment #${idx+1} missing content"
                } else {
                    try {
                        Base64.decoder.decode(att.content)
                    } catch (Exception e) {
                        errors << "Attachment #${idx+1} content is not valid Base64"
                    }
                }
            }
        }
        
    } catch (Exception e) {
        errors << "JSON is not parsed: ${e}"
    }

    
    message.setProperty("mp_Errors", errors)
    message.setProperty("mp_InboundValid", errors.isEmpty())
    message.setProperty("mp_InboundJSON", message.getBody(String))
    return message
    
}

Message storeAccountID(Message message) {
    
    def body = message.getBody(String)
    def salesOrg = message.getProperty("mp_CustomerSalesOrgID")
    
    def json = new JsonSlurper().parseText(body)
    def accountID = ""
    def agency_KUT = "" //SE2-626
    json?.d?.results?.each { account ->
        if (account.CorporateAccountSalesData?.any { 
                        it?.SalesOrganisationID == salesOrg } ) {
            accountID  = account.AccountID
            agency_KUT = account.Agency_KUT //SE2-626
            return
        }
    }
    
    def errors = []
    if (!accountID){
        errors << "Account with specified legacy number is not found!"
    }
    
    message.setProperty("mp_Errors", errors)
    if (!errors.isEmpty()) {
        throw new Exception("Account with specified legacy number is not found!")
    }
    
    message.setProperty("mp_CustomerID", accountID)
	message.setProperty("mp_Agency_KUT", agency_KUT) //SE2-626
    return message 
    
}

Message prepareTicket(Message message) {
  
    def body = message.getProperty("mp_InboundJSON")
    def json = new JsonSlurper().parseText(body)
    def partyId  = message.getProperty("mp_CustomerID")
    def agency_KUT  = message.getProperty("mp_Agency_KUT") //SE2-626	
    def salesOrg = message.getProperty("mp_CustomerSalesOrgID") //SE2-626
    def testSampleLensesQuantity = message.getProperty("mp_testSampleLensesQuantity")
	//SE2-626
	def supportTeamPartyId = json.team?.toString()?.trim()
	if (salesOrg == 'FR31') {
		def agencyKey = agency_KUT?.toString()?.trim()?.toLowerCase() //SE2-626
		def agencyTeamMap = [
			"agence - toulouse": "BBGRFRAGENCETOULOUSE",
			"agence - rennes"  : "BBGRFRAGENCERENNES",
			"agence - lyon"    : "BBGRFRAGENCELYON",
			"agence - lille"   : "BBGRFRAGENCELILLE",
			"agence - provins" : "BBGRFRAGENCEPROVINS"
		]
		def defaultTeam = "BBGRFRBACKOFFICE"

		if (!supportTeamPartyId) {
			supportTeamPartyId = agencyTeamMap[agencyKey] ?: defaultTeam
			message.setProperty("mp_C1_supportTeamPartyId", supportTeamPartyId)
			message.setProperty("mp_C1_agencyKey", agencyKey)
		}
	}
	// / SE2-626
	
    def jsonMap = [
        
        BuyerPartyID: partyId,
        Name: json.subject,
        ServiceIssueCategoryID: json.serviceCategory ?: "",
        IncidentServiceIssueCategoryID: json.incidentCategory ?: "",
        ObjectServiceIssueCategoryID: json.objectCategory ?: "",
        CauseServiceIssueCategoryID: json.causeCategory ?: "",
        ServiceSupportTeamPartyID: supportTeamPartyId, //SE2-626
        ServiceRequestUserLifeCycleStatusCode: json.status,
        SalesOrganisationID: salesOrg, //SE2-626
        
        Source_KUT : "211",
        
        ReportedPartyID: json.reportedPartyID ?: "",
        ReportedForPartyID: json.reportedForPartyID ?: "",
        
       // ZReportedByName_SDK: json.zReportedByName ?: "", //SE2-626
       // ZReportedBy_SDK: json.zReportedBy ?: "", //SE2-626

        RequestType_KUT: json.requestType,
        CustomerNumber_KUT: json.customerNumberLegacy,
        IstherequestforaMedicalProfessionalorforanOptician_KUT: json.istherequestforaMedicalProfessionalorforanOptician,
        TestSampleLenses_KUT: json.testSampleLenses,
        TestSampleLensesQuantity_KUT: testSampleLensesQuantity,
        DataOriginTypeCode: "11",
      
        ServiceRequestTextCollection: [],
        ServiceRequestAttachmentFolder: []
    ]
    
    // Conditionally
    if (json.containsKey('approvalRequired')) {
        jsonMap.ApprovalRequired_KUT = json.approvalRequired //add ApprovalRequired_KUT only if present
    }
    //SE2-626 Conditionally
    if (salesOrg == 'FR11') {
       jsonMap.ZReportedByName_SDK = json.zReportedByName ?: ""
       jsonMap.ZReportedBy_SDK     = json.zReportedBy ?: ""
    }
    if (salesOrg == 'FR31') {
		if (json.containsKey('orderNumber')) {
			jsonMap.OrderNumber_KUT = json.orderNumber 
		}
		if (json.containsKey('wearerReference')) {
			jsonMap.wearer_reference_2_SDK = json.wearerReference 
		}
		if (json.containsKey('reportedBy')) {
			jsonMap.ZReportedBy_SDK = json.reportedBy
		}
    }
	// /SE2-626
	
    json.attachments.each { attJson ->
        jsonMap.ServiceRequestAttachmentFolder << [
            Name: attJson.fileName,
            Binary: attJson.content,
            MimeType: attJson.mimeType
        ]
    }
    
    if (json.description) {
        jsonMap.ServiceRequestTextCollection << [
            TypeCode : "10004",
            Text: json.description
        ]
    }
    
    def sLang = json.language ?: "fr-FR"
    
    message.setHeader("Accept", "application/json")
    message.setHeader("Content-Type", "application/json")
    message.setHeader("Accept-Language", sLang)
    
    
    def xsrf = message.getProperty("mp_xcsrfSMC")
    if (xsrf) message.setHeader("x-csrf-token", xsrf)
    
    message.setBody(JsonOutput.toJson(jsonMap))

    return message
}

def parseResponseC4C(Message message) {
    def body = message.getBody(String)
    def json = new JsonSlurper().parseText(body)
    def id = json?.d?.results?.ID ?: ""
    def objectId = json?.d?.results?.ObjectID ?: ""
    message.setProperty("mp_TicketID", id)
    message.setProperty("mp_TicketObjectID", objectId)
    return message
}

def prepareResponse(Message message) {
    
    def props = message.getProperties()
    def headers = message.getHeaders()
    
    def errors = props.get("mp_Errors") ?: []
    def exception = props.get("CamelExceptionCaught")
    if (exception instanceof Exception) {
        errors << exception.getMessage()
    } else if (exception != null) {
        errors << exception.toString()
    }
    
    def response = [
        error          : !errors.isEmpty() || exception != null,
        ticketId       : props.get("mp_TicketID") ?: null,
        ticketObjectID : props.get("mp_TicketObjectID") ?: null,
        messages       : errors
    ]
    def jsonResponse = JsonOutput.toJson(response)
    
    message.setBody(jsonResponse)
    message.setHeader("Content-Type", "application/json")
    return message
    
}

def Message logError(Message message) {

    Map<String, Object> headers = message.getHeaders()
    StringBuilder headerLog = new StringBuilder("=== Headers ===\n")
    headers.each { key, value ->
        headerLog.append("$key : $value\n")
    }
    Map<String, Object> properties = message.getProperties()
    StringBuilder propertyLog = new StringBuilder("=== Properties ===\n")
    properties.each { key, value ->
        propertyLog.append("$key : $value\n")
    }
    def body = message.getBody(String)
    StringBuilder bodyLog = new StringBuilder("=== Body ===\n$body\n")

    StringBuilder fullLog = new StringBuilder()
    fullLog.append(headerLog)
    fullLog.append("\n")
    fullLog.append(propertyLog)
    fullLog.append("\n")
    fullLog.append(bodyLog)

    def exceptionMessage = message.getProperty("CamelExceptionCaught")?.getMessage()
    if (exceptionMessage != null) {
        fullLog.append("\n=== Error Message ===\n")
        fullLog.append(exceptionMessage)
        message.setProperty("ErrorMessage", exceptionMessage)
    }

    def timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
    def filename = "Log_${timestamp}.txt"

    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
        messageLog.addAttachmentAsString(filename, fullLog.toString(), "text/plain")
    }

    return message
    
}