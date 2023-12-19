<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  Tests that trader address fields are populated for a non-GB EORI/TIN
-->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
    <err:Error>
        <err:RaisedBy>ChRIS</err:RaisedBy>
        <err:Number>8254</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Name' within Goods Item.Notify Party is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>ChRIS</err:RaisedBy>
        <err:Number>8255</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Street and Number' within Goods Item.Notify Party is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>ChRIS</err:RaisedBy>
        <err:Number>8256</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Postcode' within Goods Item.Notify Party is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>ChRIS</err:RaisedBy>
        <err:Number>8257</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'City' within Goods Item.Notify Party is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
    <err:Error>
        <err:RaisedBy>ChRIS</err:RaisedBy>
        <err:Number>8258</err:Number>
        <err:Type>business</err:Type>
        <err:Text>The field 'Country Code' within Goods Item.Notify Party is required if a non-GB TIN has been declared (C501)</err:Text>
        <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
</err:ErrorResponse>
