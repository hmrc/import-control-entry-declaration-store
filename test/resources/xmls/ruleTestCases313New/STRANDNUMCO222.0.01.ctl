<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8642</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Street and Number (box 2)' within Goods Item.Trader Consignor must not be present if the GB 'TIN' has been declared (C501)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/TRACONCO2[1]/StrAndNumCO222[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8193</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If the Trader Consignor field is used within the Goods Item section then there must be at least two declared and they must be different.</err:Text>
    <err:Location>/ie:CC313A[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
