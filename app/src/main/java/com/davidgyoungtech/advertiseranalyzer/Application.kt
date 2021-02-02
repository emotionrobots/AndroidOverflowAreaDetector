package com.davidgyoungtech.advertiseranalyzer

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log

@ExperimentalStdlibApi
class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        this.startScanning()
    }

    fun startScanning() {
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val scanner = bluetoothAdapter.bluetoothLeScanner
        scanner.startScan(bleScannerCallback)
    }



    var lastChangeDetectionTime: Long = 0;
    var lastBinaryString: String = "";
    var servicesForBitPosition = HashMap<Integer, ArrayList<String>>()
    var presumedServiceUUidNumber = 0
    var dumped = false

    // Ble callback handler
    private val bleScannerCallback = object :ScanCallback() {

        fun extractBeaconBytes(byteBuffer: MutableList<UByte>, countToExtract:Int ) : MutableList<UByte> {
            // Android byte bit ordering is reversed from iOS
            var bitBuffer = HammingEcc().bytesToBits(byteBuffer, true)
            val byteBuffer = HammingEcc().bitsToBytes(bitBuffer)

            val matchingByte : UByte = 0xAAu
            var payload : MutableList<UByte> = arrayListOf()

            var bytePosition = 8
            var buffer = byteBuffer.drop(bytePosition).toMutableList()

            bitBuffer = HammingEcc().bytesToBits(buffer)
            val hammingBitsToDecode = 8 * (countToExtract+1) + 7

            bitBuffer = bitBuffer.dropLast(bitBuffer.size - hammingBitsToDecode).toMutableList()

            val goodBits = HammingEcc().decodeBits(bitBuffer)
            if (goodBits.size > 0) {
                var bytes = HammingEcc().bitsToBytes(goodBits)
                if (bytes[0] == matchingByte)
                    payload = bytes.drop(1).toMutableList()
                else
                    println("This is not our overflow advert")
            }
            else {
                println("Overflow area advert does not have our beacon data or its corrupted")
            }

            return payload
        }

        // Below onScanResult is called for every Service UUID returned from BLE scan
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.scanRecord?.getManufacturerSpecificData(0x004c)?.let {
                val manData : UByteArray = it.toUByteArray()
                // Overflow packet has '1' after 0x004c, and 16-bytes after the '1'
                if (manData.count() >= 17 && manData.get(0).toUByte() == 1.toUByte()) {
                    val payload = extractBeaconBytes(manData.drop(1).toMutableList(), 4)
                    val major = payload[0] * 256u + payload[1]
                    val minor = payload[2] * 256u + payload[3]
                    println("----------   Major = $major  Minor = $minor")
                }
            }
        }

        //--------------------------------------------------------------------------------------
        // Dump the UUID for each of the 128 bit positions
        //--------------------------------------------------------------------------------------
        fun dumpTable() {
            Log.d(TAG, "// Table of known service UUIDs by position in Apple's proprietary background service advertisement")
            for (bitPosition in 0..127) {
                val uuid = servicesForBitPosition.get(Integer(bitPosition))
                val first = uuid?.get(0);
                Log.d(TAG, "\""+first+"\",")
            }

        }

        //--------------------------------------------------------------------------------------
        // Given an integer, format it as a 16-byte UUID:  00000000-0000-0000-0000-000000000000
        //--------------------------------------------------------------------------------------
        fun formatServiceNumber(number : Integer) : String {
            var serviceHex = String.format("%032X", number)
            val idBuff = StringBuffer(serviceHex)
            idBuff.insert(20, '-')
            idBuff.insert(16, '-')
            idBuff.insert(12, '-')
            idBuff.insert(8, '-')
            return idBuff.toString()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            Log.d(TAG,"onBatchScanResults:${results.toString()}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "onScanFailed: $errorCode")
        }
    }
    companion object {
        public const val TAG = "AdvertiserAnalyzerApp"

    }
}

//
//  HammingEcc.swift
//  OverflowAreaBeaconRef
//
//  Based on implementation by Isuru Pamuditha https://medium.com/swlh/hamming-code-generation-correction-with-explanations-using-c-codes-38e700493280
//  Created by David G. Young on 9/9/20.
//  Copyright © 2020 davidgyoungtech. All rights reserved.
//

/*
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
                    if ((byte.toByte() and (1 shl (7 - bit)).toByte()) > 0)
                        bitValue = 1
                    else
                        bitValue = 0
                } else {
                    if ((byte.toByte() and (1 shl bit).toByte()) > 0)
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

*/

