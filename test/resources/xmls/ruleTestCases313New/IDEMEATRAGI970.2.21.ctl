<?xml version="1.0" encoding="UTF-8"?>
<!--
  Test case control file
  -->
<err:ErrorResponse xmlns:err="http://www.govtalk.gov.uk/CM/errorresponse" xmlns:dsl="http://decisionsoft.com/rim/errorExtension" SchemaVersion="2.0">
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8198</err:Number>
    <err:Type>business</err:Type>
    <err:Text>Identity of Means of Transport at Border (ex Box 21)' can not be used if 'Identity crossing border (box 21)' is present OR 'Transport Mode at Border (box 25)' = '4' (C019)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[2]/IDEMEATRAGI970[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8112</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If first digit of 'Specific Circumstance Indicator' is 'D' THEN 'Transport Mode at Border' cannot be '1', '3', '4', '8', '10' or '11' (C529)</err:Text>
    <err:Location>/ie:CC313A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8118</err:Number>
    <err:Type>business</err:Type>
    <err:Text>If 'Transport Mode at Border = '4' Then 'Conveyance Reference Number' consists of the (IATA) flight number and has format an..8 of which an..3 is the mandatory prefix identifying the airline/operator: n..4 is the mandatory number of the flight and a1 is the optional suffix (TR0518)</err:Text>
    <err:Location>/ie:CC313A[1]/HEAHEA[1]</err:Location>
  </err:Error>
  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8147</err:Number>
    <err:Type>business</err:Type>
    <err:Text>The field 'Nationality crossing border (ex. Box 21)' is required if 'Transport mode at border (box 25)' does not equal '2' (C020)</err:Text>
    <err:Location>/ie:CC313A[1]/GOOITEGDS[2]/IDEMEATRAGI970[1]</err:Location>
  </err:Error>

  <err:Error>
    <err:RaisedBy>ChRIS</err:RaisedBy>
    <err:Number>8206</err:Number>
    <err:Type>business</err:Type>
    <err:Text>[Total number of items] must equal the number of [Goods item] present.</err:Text>
    <err:Location>/ie:CC313A[1]/HEAHEA[1]</err:Location>
  </err:Error>
</err:ErrorResponse>
