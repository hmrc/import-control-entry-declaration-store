<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  Tests that trader address fields are populated for a non-GB EORI/TIN
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse"
                   SchemaVersion="2.0">
    <err:Application>
        <err:MessageCount>1</err:MessageCount>
    </err:Application>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8220</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Name (box 2)' within Import Operation.Trader Consignor is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACONCO1[1]</err:Location>
    </err:Error>
</err:ErrorResponse>
