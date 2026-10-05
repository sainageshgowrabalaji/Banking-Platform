# 0009. ISO 20022 messages written and read with the JDK

Status. Accepted, October 2026.

## Context

The payments service needs three ISO 20022 messages, pain.001, pacs.008 and pacs.002. The usual way
is to generate Java classes from the official XSD files with JAXB. That gives hundreds of classes
for the three messages, most of them for fields this project never sets.

## Decision

Each message is one small record with a method that writes its XML and one that reads it, using the
XML code in the JDK. Only the fields the platform uses are written. The reader switches off DTDs and
outside entities, so a hostile file cannot make the server read its own disk.

## What follows

The code shows exactly which elements matter and can be read in a few minutes. There is no generated
code and no extra library. The cost is that the messages are not validated against the official
schemas, so a real connection to a network would need that added, most likely by moving to generated
classes at that point.
