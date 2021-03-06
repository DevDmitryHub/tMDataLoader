package com.thomsonreuters.lsps.transmart.files
/**
 * CelFilesConverter
 * Created by bondarev on 3/25/14.
 */
class VcfFile extends CsvLikeFile implements MetaInfoHeader {
    private Entry currentEntry = new Entry()
    private String[] _samples
    private Map<CharSequence, InfoField> infoFields
    private int firstSampleIndex
    private int chromColumnIndex
    private int posColumnIndex
    private int idColumnIndex
    private int refColumnIndex
    private int altColumnIndex
    private int qualColumnIndex
    private int filterColumnIndex
    private int infoColumnIndex
    private int formatColumnIndex

    VcfFile(File file) {
        super(file, '##')
    }

    static class SampleData {
        String allele1
        String allele2
        String alleleSeparator
    }

    static class InfoField {
        enum Type {
            Integer,
            Float,
            Flag,
            Character,
            String;
        }

        String id
        String description
        Type type
        String number
    }

    class Entry {
        public static final String UNDEFINED_VALUE = ".";

        private String[] data
        private Map samplesData
        private Map<InfoField, Object[]> infoData
        private String[] alternatives
        private String[] probesetIds

        void setData(data) {
            this.data = data
            this.samplesData = null
            this.infoData = null
            this.alternatives = (~/,/).split(alternativesString, -1)
            this.probesetIds = (~/;/).split(probesetIdsString, -1)
        }

        CharSequence getChromosome() {
            data[chromColumnIndex]
        }

        long getChromosomePosition() {
            data[posColumnIndex] as long
        }

        String getProbesetIdsString() {
            data[idColumnIndex]
        }

        String getReference() {
            data[refColumnIndex]
        }

        String getAlternativesString() {
            data[altColumnIndex]
        }

        String getQual() {
            data[qualColumnIndex]
        }

        String getFilter() {
            data[filterColumnIndex]
        }

        String getInfoString() {
            data[infoColumnIndex]
        }

        String getFormatString() {
            data[formatColumnIndex]
        }

        String[] getSampleValues() {
            data[firstSampleIndex..-1]
        }

        String[] getAlternatives() {
            alternatives
        }

        String[] getProbesetIds() {
            probesetIds
        }

        Map<CharSequence, SampleData> getSamplesData() {
            samplesData ?: (samplesData = buildSamplesData())
        }

        Map<InfoField, Object> getInfoData() {
            infoData ?: (infoData = buildInfoData())
        }

        private Map<CharSequence, SampleData> buildSamplesData() {
            Map<CharSequence, SampleData> samplesData = [:]
            int gtIndex = formatString.split(':', -1).toList().indexOf('GT')
            if (gtIndex != -1) {
                VcfFile.this.samples.eachWithIndex { sample, idx ->
                    CharSequence[] parts = data[firstSampleIndex + idx].split(':', -1)
                    SampleData sampleData = new SampleData()
                    if (gtIndex != -1) {
                        def matches = parts[gtIndex] =~ /(\d+|\.)(?:([\/|])(\d+|\.))?/
                        if (matches) {
                            sampleData.allele1 = matches[0][1]
                            sampleData.alleleSeparator = matches[0][2]
                            sampleData.allele2 = matches[0][3]
                        }
                    }
                    samplesData[sample] = sampleData
                }
            }
            samplesData
        }

        private Map<InfoField, Object[]> buildInfoData() {
            if (infoString.isEmpty() || UNDEFINED_VALUE.equals(infoString)) {
                return [:]
            }
            infoString.split(';').collectEntries {
                def parts = it.split('=', 2)
                def infoField = infoFields[parts[0]] ?: new InfoField(id: parts[0])
                String[] values
                if (parts.length > 1) {
                    values = parts[1].split(',', -1)
                } else if (infoField.type == InfoField.Type.Flag) {
                    values = ['1'] as String[]
                } else {
                    values = [] as String[]
                }
                [infoField, values]
            }
        }
    }

    public void validate() {
        prepareIfRequired()
    }
    
    @Override
    void prepare() {
        super.prepare()

        chromColumnIndex = detectColumnIndex('#CHROM')
        posColumnIndex = detectColumnIndex('POS')
        idColumnIndex = detectColumnIndex('ID')
        refColumnIndex = detectColumnIndex('REF')
        altColumnIndex = detectColumnIndex('ALT')
        qualColumnIndex = detectColumnIndex('QUAL')
        filterColumnIndex = detectColumnIndex('FILTER')
        infoColumnIndex = detectColumnIndex('INFO')
        formatColumnIndex = detectColumnIndex('FORMAT')

        firstSampleIndex = formatColumnIndex + 1
        _samples = header[firstSampleIndex..-1]
        infoFields = buildInfoFields()
    }

    private int detectColumnIndex(String columnName) {
        int idx  = header.findIndexOf(columnName.&equals)
        if (idx == -1) {
            throw new UnsupportedOperationException("Column ${columnName} was not found in ${file.name}")
        }
        return idx
    }

    String[] getSamples() {
        prepareIfRequired()
        return _samples
    }

    Map<CharSequence, InfoField> getInfoFields() {
        prepareIfRequired()
        infoFields
    }

    private Map<CharSequence, InfoField> buildInfoFields() {
        headComments.findAll { it.startsWith('INFO=') }.collectEntries { headComment ->
            String fieldDescription = (headComment =~ /^INFO=<(.*)>$/)[0][1]
            def initFields = [:]
            fieldDescription.eachMatch(/,?(\w+)=('[^']*'|"[^"]*"|[^,]*)/) {
                initFields[it[1].toLowerCase()] = it[2].charAt(0) == '\'' || it[2].charAt(0) == '\"' ? it[2][1..-2] : it[2]
            }
            [initFields.id, new InfoField(initFields)]
        }
    }

    @Override
    protected def makeEntry(String[] values) {
        currentEntry.data = super.makeEntry(values)
        return currentEntry
    }
}
