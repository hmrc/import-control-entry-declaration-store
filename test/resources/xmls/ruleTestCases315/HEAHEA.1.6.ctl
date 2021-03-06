<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8115</err:Number>
    <err:Type>business</err:Type>
    <err:Text>'Nationality of means of transport crossing border' should not be present if ('Transport mode at border (box 25)' does not = '3' and 'Transport mode at border (box 25)' does not = '10' and 'Transport mode at border (box 25)' does not = '11') and 'identity Crossing Border (box 21)' is not present (C024)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8147</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Nationality crossing border (ex. Box 21)' is required if 'Transport mode at border (box 25)' does not equal '2' (C020)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/IDEMEATRAGI970[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8112</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If first digit of 'Specific Circumstance Indicator' is 'D' THEN 'Transport Mode at Border' cannot be '1', '3', '4', '8', '10' or '11' (C529)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
