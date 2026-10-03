# 0004. Kubernetes for discovery and configuration

Status. Accepted, October 2026.

## Context

The first version of this project had a Eureka server for service discovery and a Spring Cloud Config
server for configuration. That was the standard Spring Cloud setup for years. Most banks now run their
services on Kubernetes or OpenShift, which provides both.

## Decision

There is no Eureka server and no Config server. A service finds another by its Kubernetes Service name.
Configuration comes from environment variables, ConfigMaps and Secrets. On a laptop, services use
`localhost` addresses that environment variables can replace.

## What follows

Two fewer services to run, secure and keep alive, and the setup matches what most finance teams deploy
today. The cost is that discovery on a laptop is static. The ideas behind Eureka are still explained in
the learning path, because interviewers ask about them.
