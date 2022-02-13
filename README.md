# 💿 lavaplayer

An audio player library written in Java which can load audio tracks from various sources and convert them
into a stream of Opus frames. 

It is designed for use with Discord bots, but it can be used anywhere where Opus format
output is required.

## 👁️ changes

This fork has been heavily modified for use within my bot, [**Mixtape**](https://mixtape.systems). There are _many_ breaking changes that have been made and I've tracked not a single one of them, so don't expect a smooth migration.

That being said, don't expect me to help you with migrating. If you however want to give it a shot I might help you in my [Development Server](https://mixtape.systems/development).

###### _Good Luck!_

## 📦 modules

- **[lava-common](/lava-common):** common tools
- **[lava-ext-ip-rotator](/lava-ext-ip-rotator):** adds ip rotation utilities
- **[lava-ext-format-xm](/lava-ext-format-xm):** adds format for XM
- **[lava-natives](/lava-natives):** native libraries used by lavaplayer
- **[lava-track-info](/lava-track-info):** track info classes
- **[lavaplayer](/lavaplayer):** the main module
- **[testbot](/testbot):** testing bot used in development

---

Licensed under **Apache 2.0**
