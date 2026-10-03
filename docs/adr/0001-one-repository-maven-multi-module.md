# 0001. One repository and one Maven build

Status. Accepted, October 2026.

## Context

The platform has eleven services and three shared libraries. They could live in fourteen repositories
or in one. The first version of this project kept each service as its own standalone Spring project.

## Decision

Keep everything in one repository with one Maven multi-module build. The root `pom.xml` owns every
version. Maven is used because it is the most common build tool in bank Java teams.

## What follows

One command builds and tests everything, and a change to a shared library is checked against every
service at once. The services stay independent at run time, each with its own jar, image and database.
The cost is that the build grows with the project, which is handled by building only the modules that
changed.
