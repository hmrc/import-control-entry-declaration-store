<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8153</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If MESSAGE.GOODS.ITEM.PACKAGES 'Number of Packages' is '0' then there should exist at least one GOODS ITEM with the same 'Marks and Numbers of Packages' and 'Number of Packages' with value greater than '0' (TR0022)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PACGS2[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8149</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The fields 'Number of Packages (box 31)' and 'Number of Pieces (box31)' cannot be used if 'Kind of Packages (box31)' indicates 'BULK' (UNECE rec 21 : 'VQ', 'VG', 'VL', 'VY', 'VR', 'VS' or 'VO') (C062)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PACGS2[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
