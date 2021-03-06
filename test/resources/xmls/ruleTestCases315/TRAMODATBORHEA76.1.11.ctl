<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8111</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If first digit of 'Specific Circumstance Indicator' is 'C' THEN 'Transport Mode at Border' cannot be '1', '2', '4', '8', '10' or '11' (C529)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8107</err:Number>
    <err:Type>business</err:Type>
    <err:Text>Identity of means of transport crossing border' should be present if 'Transport mode at border' equals '1' or 'Transport mode at border' equals '8' (C017)</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8114</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If 'Transport Mode at Border' = '1' or '8' Then 'Identity of means of transport crossing border' consists either of the International Maritime organisation (IMO) ship identification number (format n7 – leading '0' allowed) OR of the European Vessel Identification Number (ENI) (format n8 – leading '0' allowed) (C514)</err:Text>
    <err:Location>/ie:CC315A[1]/GOOITEGDS[1]/IDEMEATRAGI970[1]/IdeMeaTraGIMEATRA971[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8690</err:Number>
    <err:Type>business</err:Type>
    <err:Text>[Transport mode at border] must not contain leading zeros</err:Text>
    <err:Location>/ie:CC315A[1]/HEAHEA[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
