<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8117</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The 'Total Number of Packages' is equal to the sum of all 'Number of Packages' + all 'Number of pieces' + a value of '1' for each declared 'bulk' (R105)</err:Text>
    <err:Location>/ie:CC313A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8150</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Number of Pieces (box 31)' is required but 'Number of packages' can not be used if 'Kind of packages (box 31)' indicates 'UNPACKED' (UNECE rec 21 : = 'NE', 'NF' or 'NG') (C062)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[1]/PACGS2[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
