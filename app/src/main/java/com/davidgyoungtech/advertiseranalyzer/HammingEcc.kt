package com.davidgyoungtech.advertiseranalyzer

import kotlin.experimental.and

class HammingEcc {

    fun bitsToBytes(bits: MutableList<Int>, bigEndian: Boolean = false): MutableList<UByte> {
        var bytes: MutableList<UByte> = arrayListOf()
        val byteCount = (bits.size + 7) / 8
        for (byteNum in 0..(byteCount - 1)) {
            var byteValue: UByte = 0u
            for (bit in 0..7) {
                var bitNum: Int = byteNum * 8 + bit
                if (bits[bitNum] == 1) {
                    if (bigEndian == true)
                        byteValue = byteValue or (1 shl (7 - bit)).toUByte()
                    else
                        byteValue = byteValue or (1 shl bit).toUByte()
                }
            }
            bytes.add(byteValue)
        }
        return bytes
    }

    fun bytesToBits(bytes: MutableList<UByte>, bigEndian: Boolean = false): MutableList<Int> {
        var bits: MutableList<Int> = arrayListOf()
        for (byte in bytes){
            for (bit in 0..7) {
                var bitValue: Int
                if (bigEndian) {
                    if ((byte and (1 shl (7 - bit)).toUByte()) > 0.toUByte())
                        bitValue = 1
                    else
                        bitValue = 0
                } else {
                    if ((byte and (1 shl bit).toUByte()) > 0.toUByte())
                        bitValue = 1
                    else
                        bitValue = 0
                }
                bits.add(bitValue)
            }
        }
        return bits
    }


    fun encodeBits(inputBits: MutableList<Int>): MutableList<Int> {
        var outputBits: MutableList<Int> = arrayListOf()
        var partyBitCount = 0
        var pos = 0
        var position = 0
        while (inputBits.size > ((1 shl pos) - (pos + 1))) {
            partyBitCount++
            pos++
        }

        var parityPos = 0
        var nonPartyPos = 0
        var i = 0
        while (i < (partyBitCount + inputBits.size)) {
            if (i == ((1 shl parityPos)-1)) {
                outputBits.add(0)
                parityPos++
            }
            else {
                outputBits.add(inputBits[nonPartyPos])
                nonPartyPos++
            }
            i++
        }

        i = 0
        while (i < partyBitCount) {
            position = (1 shl i)
            var s = 0
            var count = 0
            s = position - 1
            while (s < (partyBitCount + inputBits.size)) {
                var j = s
                while (j < (s + position)) {
                    if ((outputBits.size > j) && (outputBits[j] == 1))
                        count++
                    j++
                }
                s = s + 2 * position
            }

            if ((count % 2) == 0)
                outputBits[position-1] = 0
            else
                outputBits[position-1] = 1
            i++
        }

        var extraParity = 0
        for (bit in outputBits) {
            extraParity = extraParity or bit
        }
        outputBits.add(extraParity)

        return outputBits
    }


    @ExperimentalStdlibApi
    fun decodeBits(inputBits: MutableList<Int>): MutableList<Int> {
        var outputBits:MutableList<Int> = arrayListOf()
        var ss = 0
        var error = 0
        var parityBitsRemoved = 0
        var workBits = inputBits
        var extraParity = workBits.last()

        workBits.removeLast()

        val length = workBits.size
        var parityCount = 0

        var pos = 0
        while ((inputBits.size - parityCount) > ((1 shl pos) - (pos+1))) {
            parityCount++
            pos++
        }

        /* Check whether there are any errors */
        var i = 0
        while (i < parityCount) {
            var count = 0
            var position = (1 shl i)
            ss = position - 1
            while (ss < length) {
                var sss = ss
                while (sss < (ss + position)) {
                    if ((sss < workBits.size) && (workBits[sss] == 1))
                        count++
                    sss++
                }
                ss = ss + 2 * position
            }

            if ((count % 2) != 0) {
                error = error + position
            }
            i++
        }

        if (error != 0) {
            if (workBits[error-1] == 1)
                workBits[error-1] = 0
            else
                workBits[error-1] = 1

            i = 0
            while (i < length) {
                if (i == ((1 shl parityBitsRemoved)-1)) {
                    parityBitsRemoved++
                }
                else {
                    if (workBits.size > i)
                        outputBits.add(workBits[i])
                    else
                        outputBits.add(0)
                }
                i++
            }
        }
        else {
            i = 0
            while (i < length) {
                if (i == ((1 shl parityBitsRemoved)-1))
                    parityBitsRemoved++
                else
                    outputBits.add(workBits[i])
                i++
            }
        }

        var parity = 0
        for (bit in outputBits) {
            parity = parity or bit
        }

        if (parity != extraParity)
            return arrayListOf()

        return outputBits

    }

    @OptIn(ExperimentalStdlibApi::class)
    fun test() {
        var ecc = HammingEcc()
        var bits = mutableListOf(
            1, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            1, 0, 0, 0, 1, 0, 1, 0,
            0, 1, 0, 1, 0, 0, 0, 1,
            0, 0, 0, 0, 0, 1, 0, 0,
            0, 0, 0, 0, 0, 1, 1, 0,
            1, 0, 1, 0, 0, 0, 0, 0,
            1, 1, 0, 0, 1, 1, 1, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0)

        val bytes = ecc.bitsToBytes(bits)
        val recovered_bits = ecc.bytesToBits(bytes)
        var encoded_bits = ecc.encodeBits(bits)
        var decoded_bits = ecc.decodeBits(encoded_bits)
        var decoded_bytes = ecc.bitsToBytes(decoded_bits)

        println("bits = $bits")
        println("bytes = $bytes")
        println("recovered bytes = $recovered_bits")
        println("encoded bits = $encoded_bits")
        println("decoded bits = $decoded_bits")
        println("decoded bytes = $decoded_bytes")
    }
}