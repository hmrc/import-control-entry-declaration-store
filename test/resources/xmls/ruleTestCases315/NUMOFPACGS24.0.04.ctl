<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8680</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The 'Number of Packages' field within 'Packages' must not contain leading zeros.</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/PACGS2[1]/NumOfPacGS24[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8117</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The 'Total Number of Packages' is equal to the sum of all 'Number of Packages' + all 'Number of pieces' + a value of '1' for each declared 'bulk' (R105)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8153</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If MESSAGE.GOODS.ITEM.PACKAGES 'Number of Packages' is '0' then there should exist at least one GOODS ITEM with the same 'Marks and Numbers of Packages' and 'Number of Packages' with value greater than '0' (TR0022)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/PACGS2[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
