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
        <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/PRTNOT640[1]</err:Location>
    </err:Error>
</err:ErrorResponse>
