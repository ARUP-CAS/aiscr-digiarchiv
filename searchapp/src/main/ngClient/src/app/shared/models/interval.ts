export class Interval {
    public currentOd: number;
    public currentDo: number;
    constructor(public minRok: number, public maxRok: number) {
        this.currentOd = minRok;
        this.currentDo = maxRok;
    }
}