<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  Tests that trader address fields are populated for a non-GB EORI/TIN
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse"
                   SchemaVersion="2.0">
    <err:Application>
        <err:MessageCount>5</err:MessageCount>
    </err:Application>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8225</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Name (box 8)' within Import Operation.Trader Consignee is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC315A[1]/TRACONCE1[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8226</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Street and Number (box 8) within Import Operation.Trader Consignee is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC315A[1]/TRACONCE1[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8227</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Postcode (box 8)' within Import Operation.Trader Consignee is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC315A[1]/TRACONCE1[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8228</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'City (box 8)' within Import Operation.Trader Consignee is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC315A[1]/TRACONCE1[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>HMRC</err:RaisedBy>
        <err:Number>8229</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Country (box 8)' within Import Operation.Trader Consignee 'Country is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC315A[1]/TRACONCE1[1]</err:Location>
    </err:Error>
</err:ErrorResponse>
