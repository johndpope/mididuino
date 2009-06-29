#ifndef MIDICLOCK_H__
#define MIDICLOCK_H__

#include "WProgram.h"
#include <inttypes.h>
#include "midi-common.hh"

class MidiClockClass {
 public:
  void (*on96Callback)();
  void (*on32Callback)();
  void (*on16Callback)();

  volatile uint32_t div96th_counter;
  volatile uint32_t div32th_counter;
  volatile uint32_t div16th_counter;
  volatile uint8_t mod6_counter;
  volatile uint8_t inmod6_counter;
  volatile uint16_t interval;
  volatile uint16_t counter;
  volatile uint32_t indiv96th_counter;
  volatile uint16_t update_clock;
  volatile uint16_t update_last_clock;
  volatile uint16_t last_clock;
  volatile uint16_t rx_clock;
  volatile bool doUpdateClock;

  volatile bool updateSmaller;
  uint16_t pll_x;
  uint16_t tempo;
  bool transmit;
  bool isInit;


  volatile enum {
    PAUSED = 0,
    STARTING,
    STARTED
  } state;

#ifndef MIDIDUINO
  enum {
    OFF = 0,
    INTERNAL_MIDI,
    EXTERNAL_MIDI,
    EXTERNAL_UART2
  } mode;
  // arduino

#define BOARD_ID 0x80
#else

  enum {
    OFF = 0,
    INTERNAL,
    EXTERNAL,
    EXTERNAL_UART2
  } mode;
  #define INTERNAL_MIDI INTERNAL
  #define EXTERNAL_MIDI EXTERNAL

#endif

  MidiClockClass();
  void setOn96Callback(void (*callback)()) {
    on96Callback = callback;
  }
  void setOn32Callback(void (*callback)()) {
    on32Callback = callback;
  }
  void setOn16Callback(void (*callback)()) {
    on16Callback = callback;
  }
  void init();
  void handleClock();
  void updateClockDiff();
  void updateClockDiffTimer();
  void handleMidiStart();
  void handleMidiStop();
  void handleTimerInt();
  void start();
  void stop();
  void pause();
  void setTempo(uint16_t tempo);
  uint16_t getTempo();

  bool isStarted() {
    return state == STARTED;
  }
};

extern MidiClockClass MidiClock;

#endif /* MIDICLOCK_H__ */

