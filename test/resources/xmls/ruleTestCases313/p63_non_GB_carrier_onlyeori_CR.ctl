<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  Tests that trader address fields are populated for a non-GB EORI/TIN
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" SchemaVersion="2.0">
    <err:Application>
        <err:MessageCount>5</err:MessageCount>
    </err:Application>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8259</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Name' within Trader at Entry (Carrier) is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACARENT601[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8260</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Street and Number' within Trader at Entry (Carrier) is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACARENT601[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8261</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Postcode' within Trader at Entry (Carrier) is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACARENT601[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8262</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'City' within Trader at Entry (Carrier) is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACARENT601[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8263</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Country Code' within Trader at Entry (Carrier) is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/TRACARENT601[1]</err:Location>
    </err:Error>
</err:ErrorResponse>
